package Service;

import Model.*;
import Utility.Logger;
import java.util.*;
import java.util.concurrent.*;

// Callable implementation to allow parallel execution of team formation
public class ParallelTeamFormationCallable implements Callable<TeamBuilder> {

    private final List<Participant> participants; // Participants to group into teams
    private final int teamSize; // Size of each team
    private final ExecutorService executor; // Executor for potential parallel tasks during formation

    public ParallelTeamFormationCallable(List<Participant> participants, int teamSize, ExecutorService executor) {
        this.participants = participants;
        this.teamSize = teamSize;
        this.executor = executor;
    }

    @Override
    public TeamBuilder call() {
        long startTime = System.currentTimeMillis();
        Logger.info("Starting team formation with " + participants.size() + " participants");

        // Handles the actual team assignment logic
        TeamBuilder builder = new TeamBuilder(participants, teamSize, executor);
        builder.formTeams();

        long totalTime = System.currentTimeMillis() - startTime;
        Logger.info("Team formation completed in " + totalTime + "ms");
        System.out.println(" TEAMS FORMED IN " + totalTime + "ms");

        return builder; // Return the builder containing the formed teams
    }
}
