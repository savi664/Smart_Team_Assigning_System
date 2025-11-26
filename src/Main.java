import Model.*;
import Service.CSVHandler;
import Service.PersonalityClassifier;
import Service.TeamBuilder;
import Service.SurveyCallable;
import Service.FileReadCallable;
import Service.TeamFormationCallable;
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

    public static void main(String[] args) {
        Logger.initialize();
        Logger.info("Application started by user");

        while (true) {
            showActorMenu();
            int choice = getUserInput("Choose (1=Participant, 2=Organizer, 3=Exit): ", 1, 3);
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
            System.out.println("3. Update My Info");
            System.out.println("4. Back");
            int choice = getUserInput("Choose (1–4): ", 1, 4);
            if (choice == 4) {
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
                } else if (choice == 3) {
                    Logger.info("User selected: Update My Info");
                    updateParticipantInfo();
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

        String id = getInput("Enter ID: ").toUpperCase();
        if(containsID(id)){
            Logger.error("ID already exists!");
            System.out.println("Please re-enter ID as the ID you have entered belongs to another person");
            return;
        }
        String name = getInput("Enter Name: ");
        String email = getValidEmail();
        String game = getInput("Enter Preferred Game: ");
        int skill = getUserInput("Enter Skill Level (1–10): ", 1, 10);
        RoleType role = getValidRole();

        System.out.println("Starting personality survey...");
        Logger.info("Personality survey started for participant: " + id);

        // Create SurveyCallable and submit to executor
        SurveyCallable surveyCallable = new SurveyCallable(id, name, email, game, skill, role, classifier);
        Future<Participant> futureParticipant = executor.submit(surveyCallable);

        try {
            Participant participant = futureParticipant.get(); // Wait for completion
            Logger.info("Participant created: " + id + " (" + name + ")");

            if (teamBuilder != null && formedTeams != null) {
                Team fittingTeam = teamBuilder.findSuitableTeam(participant);
                if (fittingTeam != null) {
                    fittingTeam.addMember(participant);
                    Logger.info("Participant " + id + " added to Team " + fittingTeam.getTeam_id());
                }
            }

            CSVHandler.addToCSV(participant);
            Logger.info("Participant " + id + " saved to CSV");
            allParticipants = null;
            System.out.println("Registered and saved to CSV!");
        } catch (Exception e) {
            Logger.error("Registration failed: " + e.getMessage());
            System.out.println("Error during registration: " + e.getMessage());
        }
    }

    private static boolean containsID(String id) throws InvalidSurveyDataException, IOException {

        //Get all the participants in a List
        List<Participant> participants = CSVHandler.readCSV("participants_sample.csv");

        // return if the entered participant is there ot not
        return participants.stream().anyMatch(participant -> participant.getId().equals(id));
    }

    private static void checkMyTeam() {
        Logger.info("User checking their team");

        if (teamBuilder == null || formedTeams == null) {
            Logger.warning("Teams not formed yet");
            System.out.println("Teams not formed yet. Ask organizer.");
            return;
        }

        String id = getInput("Enter your ID: ");
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

    private static void updateParticipantInfo() throws IOException, SkillLevelOutOfBoundsException, InvalidSurveyDataException {
        String id = getInput("Enter your ID: ");
        Logger.info("Update info requested for participant: " + id);

        List<Participant> allFromCSV = CSVHandler.readCSV("participants_sample.csv");
        Participant participant = findParticipantById(allFromCSV, id);

        if (participant == null) {
            Logger.warning("Participant not found: " + id);
            System.out.println("Participant not found.");
            return;
        }

        displayCurrentInfo(participant);

        System.out.println("\nWhat do you want to change?");
        System.out.println("1. Email");
        System.out.println("2. Preferred Game");
        System.out.println("3. Skill Level");
        System.out.println("4. Preferred Role");
        int choice = getUserInput("Choose (1–4): ", 1, 4);

        String attributeName = getAttributeName(choice);
        if (attributeName == null) {
            System.out.println("Invalid choice.");
            return;
        }

        Object newValue = getNewAttributeValue(choice);

        TeamBuilder tempBuilder = new TeamBuilder(allFromCSV, currentTeamSize);
        tempBuilder.updateParticipantAttribute(participant, attributeName, newValue);

        CSVHandler.exportUnassignedUser("participants_sample.csv", allFromCSV);
        Logger.info("Participant " + id + " info updated: " + attributeName);
        System.out.println("Your info has been updated and saved to CSV.");

        if (tempBuilder.doesAttributeAffectBalance(attributeName) && teamBuilder != null && formedTeams != null) {
            System.out.println("Balance changed – re-forming teams...");
            Logger.info("Attribute change affects team balance, reforming teams");
            allParticipants = allFromCSV;
            reformTeams();
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
            int c = getUserInput("Choose (1–5): ", 1, 5);
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

            try {
                if (path.isEmpty()) {
                    Logger.warning("Empty CSV file path provided");
                    throw new InvalidCSVFilePathException("Please enter a valid CSV filepath");
                }
                break;
            } catch (InvalidCSVFilePathException e) {
                System.out.println(e.getMessage());
            }
        }

        System.out.println("\nTeam Size Configuration:");
        System.out.println("  - Minimum: 2 members");
        System.out.println("  - Maximum: 10 members");
        System.out.println("  - Current default: " + currentTeamSize);

        int teamSize = getUserInput("Enter desired team size (3-10, or 0 to use default): ", 3, 10, true);
        if (teamSize == 0) {
            teamSize = currentTeamSize;
            System.out.println("Using default team size: " + teamSize);
        } else {
            currentTeamSize = teamSize;
            System.out.println("Team size set to: " + teamSize);
        }

        System.out.println("\nReading CSV and forming teams in background...");
        System.out.println("Type anything to continue using the menu while it works.\n");

        final String finalPath = path;
        final int finalTeamSize = teamSize;

        // Step 1: Create FileReadCallable to read CSV file
        FileReadCallable fileReadCallable = new FileReadCallable(finalPath);
        Future<List<Participant>> futureParticipants = executor.submit(fileReadCallable);

        // Step 2: In a separate thread, wait for file read and then form teams
        executor.submit(() -> {
            try {
                // Wait for file reading to complete
                List<Participant> participants = futureParticipants.get();
                Logger.info("File read completed, now forming teams");

                // Create TeamFormationCallable - now returns TeamBuilder
                TeamFormationCallable teamFormationCallable = new TeamFormationCallable(participants, finalTeamSize);
                TeamBuilder builder = teamFormationCallable.call();  // Get the TeamBuilder

                // Get the formed teams from the builder
                List<Team> teams = builder.getAllTeams();

                // Update global state
                synchronized (Main.class) {
                    allParticipants = participants;
                    formedTeams = teams;
                    teamBuilder = builder;  // Store the builder that has already formed teams
                }

                Logger.info("Team formation process completed successfully");
                System.out.println("\n>>> TEAMS ARE READY! Select 'View Teams' to see them. <<<\n");
            } catch (Exception e) {
                Logger.error("Team formation process failed: " + e.getMessage());
                System.err.println("Error: " + e.getMessage());
            }
        });
    }

    private static void viewTeams() {
        Logger.info("Viewing all teams");

        if (teamBuilder == null || formedTeams == null) {
            Logger.warning("Teams not formed yet");
            System.out.println("Teams not formed yet. Choose 'Form Teams' first.");
            return;
        }
        teamBuilder.printAllTeams();
    }

    private static void removeParticipant() {
        Logger.info("Started participant removal process");

        if (teamBuilder == null || formedTeams == null) {
            Logger.warning("No participants loaded");
            System.out.println("No participants loaded.");
            return;
        }

        String id = getInput("Enter Participant ID to remove: ");
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
                CSVHandler.exportUnassignedUser("participants_sample.csv", allParticipants);
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

    private static void exportTeams() {
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
        }

        try {
            CSVHandler.toCSV(path, formedTeams);
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
            System.out.println("No participants to form teams from.");
            return;
        }

        teamBuilder = new TeamBuilder(allParticipants, currentTeamSize);
        formedTeams = teamBuilder.formTeams();
        Logger.info("Teams reformed: " + formedTeams.size() + " team(s)");
        System.out.println("Teams re-formed successfully! " + formedTeams.size() + " team(s).");
    }

    private static Participant findParticipantById(List<Participant> participants, String id) {
        for (Participant p : participants) {
            if (p.getId().equalsIgnoreCase(id)) return p;
        }
        return null;
    }

    private static void displayCurrentInfo(Participant participant) {
        System.out.println("\nCurrent Info:");
        System.out.println("  Name: " + participant.getName());
        System.out.println("  Email: " + participant.getEmail());
        System.out.println("  Game: " + participant.getPreferredGame());
        System.out.println("  Skill: " + participant.getSkillLevel());
        System.out.println("  Role: " + participant.getPreferredRole());
    }

    private static String getAttributeName(int choice) {
        if (choice == 1) return "email";
        else if (choice == 2) return "preferred game";
        else if (choice == 3) return "skill level";
        else if (choice == 4) return "preferred role";
        return null;
    }

    private static Object getNewAttributeValue(int choice) {
        if (choice == 1) return getValidEmail();
        else if (choice == 2) return getInput("New Preferred Game: ");
        else if (choice == 3) return getUserInput("New Skill Level (1–10): ", 1, 10);
        else if (choice == 4) return getValidRole().name();
        return null;
    }

    private static Team findTeamByParticipantId(String id) {
        if (formedTeams == null) return null;
        for (Team team : formedTeams) {
            if (team.containsParticipant(id) != null) return team;
        }
        return null;
    }

    private static String getInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static String getValidEmail() {
        while (true) {
            String email = getInput("Enter Email: ").trim();
            if (email.matches("^[\\w.-]+@university\\.edu$")) {
                return email;
            }
            System.out.println("Invalid email. Only @university.edu addresses are allowed.");
        }
    }

    private static RoleType getValidRole() {
        while (true) {
            System.out.print("Enter Role (STRATEGIST, ATTACKER, DEFENDER, SUPPORTER, COORDINATOR): ");
            try {
                String input = scanner.nextLine().trim().toUpperCase();
                return RoleType.valueOf(input);
            } catch (IllegalArgumentException ex) {
                System.out.println("Invalid role.");
            }
        }
    }

    private static int getUserInput(String prompt, int min, int max) {
        return getUserInput(prompt, min, max, false);
    }

    private static int getUserInput(String prompt, int min, int max, boolean allowZero) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = Integer.parseInt(scanner.nextLine().trim());
                if (allowZero) min = 0;
                if (value >= min && value <= max) return value;
                System.out.println("Enter a number between " + min + " and " + max);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Must be a number.");
            }
        }
    }
}