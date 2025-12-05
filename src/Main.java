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
    private static final CSVHandler csvHandler = new CSVHandler();

    private static List<Participant> allParticipants = null;
    private static TeamBuilder teamBuilder = null;
    private static List<Team> formedTeams = null;

    private static int currentTeamSize = 5;

    // ====================================================================================
    // MAIN
    // ====================================================================================
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

    // ====================================================================================
    // ACTOR MENU
    // ====================================================================================
    private static void showActorMenu() {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("           Team Builder");
        System.out.println("1. Participant");
        System.out.println("2. Organizer");
        System.out.println("3. Exit");
        System.out.println("=".repeat(55));
    }

    // ====================================================================================
    // PARTICIPANT MENU
    // ====================================================================================
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

        // Check duplicate in CSV
        if (csvHandler.containsID(id)) {
            Logger.error("ID already exists");
            System.out.println("This ID is already registered. Try a different one.");
            return;
        }

        String name = InputValidator.getInput("Enter Name: ");
        String email = InputValidator.getValidEmail();
        String game = InputValidator.getInput("Enter Preferred Game: ");
        int skillLevel = InputValidator.getUserInput("Enter Skill Level (1–10): ", 1, 10);
        RoleType role = InputValidator.getValidRole();

        // Personality Scan
        Logger.info("Personality survey started for participant: " + id);

        int score = classifier.CalculatePersonalityScore();
        PersonalityType personalityType = classifier.ClassifyPersonality(score);

        try {
            Participant participant = new Participant(
                    id, name, email, game, skillLevel, role, score, personalityType
            );

            Logger.info("Participant created: " + id + " (" + name + ")");

            // Auto-assign to team if teams already exist
            if (teamBuilder != null && formedTeams != null) {
                Team fittingTeam = teamBuilder.findSuitableTeam(participant);

                if (fittingTeam != null) {
                    fittingTeam.addMember(participant);
                    Logger.info("Participant " + id + " added to Team " + fittingTeam.getTeam_id());
                }
            }

            // Save to CSV
            csvHandler.addToCSV(participant);
            Logger.info("Participant " + id + " saved to CSV");

            allParticipants = null; // forces reload later

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

    // ====================================================================================
    // ORGANIZER MENU
    // ====================================================================================
    private static void organizerMenu() {
        Logger.info("Entered Organizer Menu");

        while (true) {
            System.out.println("\n--- Organizer Menu ---");
            System.out.println("1. Form Teams");
            System.out.println("2. View Teams");
            System.out.println("3. Remove Participant from Team");
            System.out.println("4. Export Teams to CSV");
            System.out.println("5. Back");

            int c = InputValidator.getUserInput("Choose (1–5): ", 1, 5);

            if (c == 5) {
                Logger.info("User exited Organizer Menu");
                break;
            }

            try {
                if (c == 1) formTeams();
                else if (c == 2) viewTeams();
                else if (c == 3) removeParticipant();
                else if (c == 4) exportTeams();
            } catch (Exception e) {
                Logger.error("Error in organizerMenu: " + e.getMessage());
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
    private static void formTeams() {
        Logger.info("Started team formation process");

        System.out.print("Enter CSV file path: ");
        String path = scanner.nextLine().trim();

        if (path.isEmpty()) {
            System.out.println("Invalid path. Try again.");
            return;
        }

        System.out.println("\nTeam Size Configuration:");
        System.out.println("  - Minimum: 2 members");
        System.out.println("  - Maximum: 10 members");
        System.out.println("  - Current default: " + currentTeamSize);

        int teamSize = InputValidator.getUserInput(
                "Enter desired team size (3–10, or 0 to use default): ",
                0, 10
        );

        if (teamSize == 0) {
            teamSize = currentTeamSize;
        } else {
            currentTeamSize = teamSize;
        }

        System.out.println("\nReading CSV and forming teams in background...");

        try {
            final int finalTeamSize = teamSize;

            // File reading task
            Future<List<Participant>> futureParticipants =
                    executor.submit(new ParallelFileReadCallable(path, executor, 4));

            // Async continuation task
            executor.submit(() -> {
                try {
                    List<Participant> participants = futureParticipants.get();

                    ParallelTeamFormationCallable formation =
                            new ParallelTeamFormationCallable(participants, finalTeamSize, executor);

                    TeamBuilder builder = formation.call();

                    synchronized (Main.class) {
                        allParticipants = participants;
                        teamBuilder = builder;
                        formedTeams = builder.getAllTeams();
                    }

                    Logger.info("Teams are ready!");
                    System.out.println("\n>>> TEAMS ARE READY! <<<\n");

                } catch (Exception e) {
                    Logger.error("Team formation failed: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            Logger.error("Failed to start team formation thread: " + e.getMessage());
            System.out.println("Failed to start team formation.");
        }
    }

    // ====================================================================================
    // VIEW TEAMS
    // ====================================================================================
    private static void viewTeams() {
        Logger.info("Viewing all teams");

        synchronized (Main.class) {
            if (formedTeams == null) {
                Logger.warning("Teams not formed yet or still forming");
                System.out.println("Teams are not ready yet.");
                return;
            }

            if (formedTeams.isEmpty()) {
                Logger.warning("Teams are empty");
                System.out.println("No teams available.");
                return;
            }

            teamBuilder.printAllTeams();
        }
    }

    // ====================================================================================
    // REMOVE PARTICIPANT
    // ====================================================================================
    private static void removeParticipant() {
        Logger.info("Started participant removal process");

        if (teamBuilder == null || formedTeams == null) {
            Logger.warning("Teams are not formed yet");
            System.out.println("Teams are not formed.");
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

        if (!removed) {
            Logger.warning("Participant not found: " + id);
            System.out.println("Participant not found.");
            return;
        }

        Logger.info("Participant " + id + " removed successfully");
        System.out.println("Participant removed.");

        // Update CSV
        try {
            csvHandler.exportUnassignedUser("participants_sample.csv", allParticipants);
            Logger.info("CSV updated after participant removal");
        } catch (IOException e) {
            Logger.error("Failed to update CSV: " + e.getMessage());
            System.out.println("Warning: Could not update CSV.");
        }

        // Rebuild teams cleanly
        reformTeams();
    }

    private static void exportTeams() throws InvalidCSVFilePathException {
        Logger.info("Started team export process");

        if (formedTeams == null) {
            Logger.warning("No teams to export");
            System.out.println("No teams to export.");
            return;
        }

        System.out.print("Enter output CSV file path (e.g., file.csv): ");
        String path = scanner.nextLine().trim();

        if (path.isEmpty()) {
            System.out.println("Using default file: teams_output.csv");
            path = "teams_output.csv";
        } else if (!path.endsWith(".csv")) {
            throw new InvalidCSVFilePathException("CSV files must end with '.csv'.");
        }

        try {
            csvHandler.toCSV(path, formedTeams);
            Logger.info("Teams exported to: " + path);
            System.out.println("Exported to " + path);
        } catch (IOException e) {
            Logger.error("Export failed: " + e.getMessage());
            System.out.println("Export failed: " + e.getMessage());
        }
    }

    private static void reformTeams() {
        if (allParticipants == null || allParticipants.isEmpty()) {
            Logger.warning("No participants to reform teams");
            System.out.println("No participants available.");
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
            Participant match = team.containsParticipant(id);
            if (match != null) return team;
        }
        return null;
    }
}
