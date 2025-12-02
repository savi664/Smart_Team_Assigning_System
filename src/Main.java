import Model.*;
import Service.*;
import Exception.*;
import Utility.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static final PersonalityClassifier classifier = new PersonalityClassifier();
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static List<Participant> allParticipants = null;
    private static TeamBuilder teamBuilder = null;
    private static List<Team> formedTeams = null;
    private static int currentTeamSize = 5;
    private static final CSVHandler csvHandler = new CSVHandler() ;

    public static void main(String[] args) {
        Logger.initialize();
        Logger.info("Application started by user");

        while (true) {
            showActorMenu();
            int choice = InputValidator.getUserInput("Choose (1=Participant, 2=Organizer, 3=Exit): ", 1, 3);
            if (choice == 3) {
                Logger.info("User selected Exit option");
                System.out.println("Goodbye!");
                break;
            }
            if (choice == 1) {
                Logger.info("User selected Participant role");
                participantMenu();
            } else {
                Logger.info("User selected Organizer role");
                organizerMenu();
            }
        }
        scanner.close();
        executor.shutdownNow();
        Logger.close();
    }

    private static void showActorMenu() {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("           Team Builder");
        System.out.println("1. Participant");
        System.out.println("2. Organizer");
        System.out.println("3. Exit");
        System.out.println("=".repeat(55));
    }

    private static void participantMenu() {
        Logger.info("Entered Participant Menu");

        while (true) {
            System.out.println("\n--- Participant Menu ---");
            System.out.println("1. Register (Take Survey)");
            System.out.println("2. Check My Team");
            System.out.println("3. Back");
            int choice = InputValidator.getUserInput("Choose (1–3): ", 1, 3);
            if (choice == 3) {
                Logger.info("User exited Participant Menu");
                break;
            }

            try {
                if (choice == 1) {
                    Logger.info("User selected: Register (Take Survey)");
                    registerParticipant();
                } else if (choice == 2) {
                    Logger.info("User selected: Check My Team");
                    checkMyTeam();
                }
            } catch (Exception e) {
                Logger.error("Error in participantMenu: " + e.getMessage());
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void registerParticipant() throws IOException, InvalidSurveyDataException {
        Logger.info("Started new participant registration");
        System.out.println("\n--- Register New Participant ---");

        String id = InputValidator.getInput("Enter ID: ").toUpperCase();
        if(csvHandler.containsID(id)){
            Logger.error("ID already exists!");
            System.out.println("Please re-enter ID as the ID you have entered belongs to another person");
            return;
        }
        String name = InputValidator.getInput("Enter Name: ");
        String email = InputValidator.getValidEmail();
        String game = InputValidator.getInput("Enter Preferred Game: ");
        int skillLevel = InputValidator.getUserInput("Enter Skill Level (1–10): ", 1, 10);
        RoleType role = InputValidator.getValidRole();

        System.out.println("Starting personality survey...");
        Logger.info("Personality survey started for participant: " + id);

        int score = classifier.CalculatePersonalityScore();
        PersonalityType personalityType = classifier.ClassifyPersonality(score);

        try {
            Participant participant = new Participant(id,name,email,game,skillLevel,role,score,personalityType); // Wait for completion
            Logger.info("Participant created: " + id + " (" + name + ")");

            if (teamBuilder != null && formedTeams != null) {
                Team fittingTeam = teamBuilder.findSuitableTeam(participant);
                if (fittingTeam != null) {
                    fittingTeam.addMember(participant);
                    Logger.info("Participant " + id + " added to Team " + fittingTeam.getTeam_id());
                }
            }

            csvHandler.addToCSV(participant);
            Logger.info("Participant " + id + " saved to CSV");
            allParticipants = null;
            System.out.println("Registered and saved to CSV!");
        } catch (Exception e) {
            Logger.error("Registration failed: " + e.getMessage());
            System.out.println("Error during registration: " + e.getMessage());
        }
    }

    private static void checkMyTeam() {
        Logger.info("User checking their team");

        if (teamBuilder == null || formedTeams == null) {
            Logger.warning("Teams not formed yet");
            System.out.println("Teams not formed yet. Ask organizer.");
            return;
        }

        String id = InputValidator.getInput("Enter your ID: ");
        Team team = findTeamByParticipantId(id);

        if (team != null) {
            Logger.info("Team found for participant " + id + ": Team " + team.getTeam_id());
            System.out.println("You are in Team " + team.getTeam_id());
            System.out.println("Team Members:");
            for (Participant p : team.getParticipantList()) {
                System.out.println("  • " + p.getName() + " (" + p.getId() + ")");
            }
        } else {
            Logger.warning("No team found for participant: " + id);
            System.out.println("You are not assigned to any team.");
        }
    }

    private static void organizerMenu() {
        Logger.info("Entered Organizer Menu");

        while (true) {
            System.out.println("\n--- Organizer Menu ---");
            System.out.println("1. Form Teams");
            System.out.println("2. View Teams");
            System.out.println("3. Remove Participant");
            System.out.println("4. Export Teams to CSV");
            System.out.println("5. Back");
            int c = InputValidator.getUserInput("Choose (1–5): ", 1, 5);
            if (c == 5) {
                Logger.info("User exited Organizer Menu");
                break;
            }

            try {
                if (c == 1) {
                    Logger.info("User selected: Form Teams");
                    formTeams();
                } else if (c == 2) {
                    Logger.info("User selected: View Teams");
                    viewTeams();
                } else if (c == 3) {
                    Logger.info("User selected: Remove Participant");
                    removeParticipant();
                } else if (c == 4) {
                    Logger.info("User selected: Export Teams to CSV");
                    exportTeams();
                }
            } catch (Exception e) {
                Logger.error("Error in organizerMenu: " + e.getMessage());
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void formTeams() {
        Logger.info("Started team formation process");

        String path;
        while (true) {
            System.out.print("Enter CSV file path : ");
            path = scanner.nextLine().trim();

            if (!path.isEmpty()) break;
            System.out.println("Please enter a valid CSV filepath.");
        }

        System.out.println("\nTeam Size Configuration:");
        System.out.println("  - Minimum: 2 members");
        System.out.println("  - Maximum: 10 members");
        System.out.println("  - Current default: " + currentTeamSize);

        int teamSize = InputValidator.getUserInput("Enter desired team size (3-10, or 0 to use default): ", 3, 10, true);
        if (teamSize == 0) teamSize = currentTeamSize;
        else currentTeamSize = teamSize;

        System.out.println("\nReading CSV and forming teams in background...");
        System.out.println("Type anything to continue using the menu while it works.\n");

        final String finalPath = path;
        final int finalTeamSize = teamSize;

        // Step 1: Submit CSV reading task
        Future<List<Participant>> futureParticipants = executor.submit(new ParallelFileReadCallable(finalPath, executor, 4));

        // Step 2: Submit team formation task that depends on CSV reading
        executor.submit(() -> {
            try {
                List<Participant> participants = futureParticipants.get(); // wait for CSV
                ParallelTeamFormationCallable formationCallable = new ParallelTeamFormationCallable(participants, finalTeamSize, executor);
                TeamBuilder builder = formationCallable.call();

                // Update shared variables safely
                synchronized (Main.class) {
                    allParticipants = participants;
                    teamBuilder = builder; // Use the builder that already has formed teams
                    formedTeams = builder.getAllTeams(); // Get the teams from the builder
                }

                Logger.info("Teams are ready!");
                System.out.println("\n>>> TEAMS ARE READY! <<<\n");

            } catch (Exception e) {
                Logger.error("Error forming teams: " + e.getMessage());
                System.out.println("Error forming teams: " + e.getMessage());
            }
        });
    }

    private static void viewTeams() {
        Logger.info("Viewing all teams");

        synchronized (Main.class) {
            if (formedTeams == null) {
                Logger.warning("Teams not formed yet or still forming");
                System.out.println("Teams are not ready yet. Please wait until team formation completes.");
                return;
            }

            if (teamBuilder == null || formedTeams.isEmpty()) {
                Logger.warning("Teams are empty");
                System.out.println("No teams available. Choose 'Form Teams' first.");
                return;
            }

            // Print teams
            teamBuilder.printAllTeams();
        }
    }

    private static void removeParticipant() {
        Logger.info("Started participant removal process");

        if (teamBuilder == null || formedTeams == null) {
            Logger.warning("No participants loaded");
            System.out.println("No participants loaded.");
            return;
        }

        String id = InputValidator.getInput("Enter Participant ID to remove: ");
        boolean removed = false;

        for (int i = 0; i < allParticipants.size(); i++) {
            if (allParticipants.get(i).getId().equalsIgnoreCase(id)) {
                allParticipants.remove(i);
                removed = true;
                break;
            }
        }

        if (removed) {
            Logger.info("Participant " + id + " removed successfully");
            System.out.println("Participant removed from list.");
            try {
                csvHandler.exportUnassignedUser("participants_sample.csv", allParticipants);
                Logger.info("CSV updated after participant removal");
                System.out.println("CSV updated.");
            } catch (IOException e) {
                Logger.error("Failed to update CSV: " + e.getMessage());
                System.out.println("Warning: Could not update CSV: " + e.getMessage());
            }
            reformTeams();
        } else {
            Logger.warning("Participant not found: " + id);
            System.out.println("Participant not found.");
        }
    }

    private static void exportTeams() throws InvalidCSVFilePathException {
        Logger.info("Started team export process");

        if (formedTeams == null) {
            Logger.warning("No teams to export");
            System.out.println("No teams to export. Form teams first.");
            return;
        }

        System.out.print("Give the CSV file path where you want the teams pasted eg:file.csv: ");
        String path = scanner.nextLine().trim();
        if (path.isEmpty()) {
            System.out.println("File path not provided using default teams_output.csv to store teams: ");
            path = "teams_output.csv";
        }else if (path.endsWith(".csv")) {
            throw new InvalidCSVFilePathException("CSV files must end with '.csv'.");
        }

        try {
            csvHandler.toCSV(path, formedTeams);
            Logger.info("Teams exported to: " + path);
            System.out.println("Exported to " + path);
        } catch (IOException e) {
            Logger.error("Export failed: " + e.getMessage());
            System.out.println("Export failed: " + e.getMessage());
        } catch (InvalidCSVFilePathException e) {
            System.out.println("Invalid CSV file path: " + e.getMessage()   );
        }
    }

    private static void reformTeams() {
        if (allParticipants == null || allParticipants.isEmpty()) {
            Logger.warning("No participants to reform teams");
            System.out.println("No participants to form teams from.");
            return;
        }

        teamBuilder = new TeamBuilder(allParticipants, currentTeamSize);
        formedTeams = teamBuilder.formTeams();
        Logger.info("Teams reformed: " + formedTeams.size() + " team(s)");
        System.out.println("Teams re-formed successfully! " + formedTeams.size() + " team(s).");
    }

    private static Team findTeamByParticipantId(String id) {
        if (formedTeams == null) return null;
        for (Team team : formedTeams) {
            if (team.containsParticipant(id) != null) return team;
        }
        return null;
    }
}