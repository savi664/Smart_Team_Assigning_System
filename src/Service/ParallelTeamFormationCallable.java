package Service;

import Model.*;
import Utility.Logger;
import java.util.*;
import java.util.concurrent.*;

public class ParallelTeamFormationCallable implements Callable<TeamBuilder> {

    private final List<Participant> participants;
    private final int teamSize;
    private final ExecutorService executor;

    public ParallelTeamFormationCallable(List<Participant> participants, int teamSize, ExecutorService executor) {
        this.participants = participants;
        this.teamSize = teamSize;
        this.executor = executor;
    }

    @Override
    public TeamBuilder call() {
        long startTime = System.currentTimeMillis();
        Logger.info("Starting team formation with " + participants.size() + " participants");

        TeamBuilder builder = new TeamBuilder(participants, teamSize, executor);
        builder.formTeams();  // ← This does the real work

        long totalTime = System.currentTimeMillis() - startTime;
        Logger.info("Team formation completed in " + totalTime + "ms");
        System.out.println(">>> TEAMS FORMED IN " + totalTime + "ms <<<");

        return builder;  // ← RETURN THE ONE THAT HAS THE TEAMS, NOT A NEW ONE
    }
}