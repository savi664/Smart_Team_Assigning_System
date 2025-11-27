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
    public TeamBuilder call() throws Exception {
        long startTime = System.currentTimeMillis();
        Logger.info("Starting team formation with " + participants.size() + " participants");

        // Pass executor to TeamBuilder for parallel processing
        TeamBuilder builder = new TeamBuilder(participants, teamSize, executor);
        builder.formTeams();

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        Logger.info("Team formation completed in " + totalTime + "ms");
        System.out.println("⚡ Formation time: " + totalTime + "ms");

        return builder;
    }
}