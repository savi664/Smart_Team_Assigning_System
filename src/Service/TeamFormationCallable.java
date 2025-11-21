package Service;

import Model.Participant;
import Model.Team;
import Utility.Logger;

import java.util.List;
import java.util.concurrent.Callable;

public class TeamFormationCallable implements Callable<List<Team>> {

    private final List<Participant> participants;
    private final int teamSize;

    public TeamFormationCallable(List<Participant> participants, int teamSize) {
        this.participants = participants;
        this.teamSize = teamSize;
    }

    @Override
    public List<Team> call() throws Exception {
        Logger.info("TeamFormationCallable: Starting team formation for " + participants.size() +
                " participants with team size: " + teamSize);

        try {
            TeamBuilder builder = new TeamBuilder(participants, teamSize);
            List<Team> teams = builder.formTeams();

            Logger.info("TeamFormationCallable: Teams formed successfully - " + teams.size() + " team(s)");
            Logger.debug("TeamFormationCallable: Team formation completed");

            return teams;
        } catch (IllegalArgumentException e) {
            Logger.error("TeamFormationCallable: IllegalArgumentException: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Logger.error("TeamFormationCallable: Error during team formation: " + e.getMessage());
            throw e;
        }
    }
}
