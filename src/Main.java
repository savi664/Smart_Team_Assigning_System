import Model.*;
import Service.CSVHandler;
import Service.PersonalityClassifier;
import Service.TeamBuilder;
import Exception.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static final CSVHandler csvHandler = new CSVHandler();
    private static final PersonalityClassifier classifier = new PersonalityClassifier();
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static List<Participant> allParticipants = null;
    private static TeamBuilder teamBuilder = null;
    private static List<Team> formedTeams = null;
    private static int currentTeamSize = 5;

    public static void main(String[] args) {
        while (true) {
            showActorMenu();
            int choice = getUserInput("Choose (1=Participant, 2=Organizer, 3=Exit): ",1, 3);
            if (choice == 3) {
                System.out.println("Goodbye!");
                break;
            }
            if (choice == 1) {
                participantMenu();
            } else {
                organizerMenu();
            }
        }
        scanner.close();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                executor.shutdownNow();
            }
        }));
    }

    // ============================= ACTOR MENU =============================
    private static void showActorMenu() {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("           Team Builder");
        System.out.println("1. Participant");
        System.out.println("2. Organizer");
        System.out.println("3. Exit");
        System.out.println("=".repeat(55));
    }

    // ============================= PARTICIPANT MENU =============================
    private static void participantMenu() {
        while (true) {
            System.out.println("\n--- Participant Menu ---");
            System.out.println("1. Register (Take Survey)");
            System.out.println("2. Check My Team");
            System.out.println("3. Update My Info");
            System.out.println("4. Back");
            int choice = getUserInput("Choose (1–4): ", 1, 4);
            if (choice == 4) {
                break;
            }

            try {
                if (choice == 1) {
                    registerParticipant();
                } else if (choice == 2) {
                    checkMyTeam();
                } else if (choice == 3) {
                    updateParticipantInfo();
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void registerParticipant() throws IOException, SkillLevelOutOfBoundsException, InvalidSurveyDataException {
        System.out.println("\n--- Register New Participant ---");

        // Checking for identical ID in the Participant list
        String id = getInput("Enter ID: ").toUpperCase();
        List<Participant> participants= csvHandler.readCSV("participants_sample.csv");
        List<String> IDList = participants.stream().map(Participant::getId).toList();
        if (IDList.contains(id)){
            System.out.println("Please enter a new ID for the participant as the id already exists");
            return;
        }


        String name = getInput("Enter Name: ");
        String email = getValidEmail();
        String game = getInput("Enter Preferred Game: ");
        int skill = getUserInput("Enter Skill Level (1–10): ",1, 10);
        RoleType role = getValidRole();

        System.out.println("Starting personality survey...");
        int[] answers = classifier.ConductSurvey();
        int score = classifier.CalculatePersonalityScore(answers);
        PersonalityType type = classifier.classifyPersonality(score);

        Participant participant = new Participant(id, name, email, game, skill, role, score, type);

        if (teamBuilder != null && formedTeams != null) {
            Team fittingTeam = teamBuilder.findFittingTeam(participant);
            if (fittingTeam != null) {
                fittingTeam.addMember(participant);
                reformTeams();
            }
        }

        csvHandler.addToCSV(participant);
        allParticipants = null;
        System.out.println("Registered and saved to CSV!");
    }

    private static void checkMyTeam() {
        if (teamBuilder == null || formedTeams == null) {
            System.out.println("Teams not formed yet. Ask organizer.");
            return;
        }

        String id = getInput("Enter your ID: ");
        Team team = findTeamByParticipantId(id);

        if (team != null) {
            System.out.println("You are in Team " + team.getTeam_id());
            System.out.println("Team Members:");

            for (Participant p : team.getParticipantList()) {
                System.out.println("  • " + p.getName() + " (" + p.getId() + ")");
            }
        } else {
            System.out.println("You are not assigned to any team.");
        }
    }

    private static void updateParticipantInfo() throws IOException, SkillLevelOutOfBoundsException, InvalidSurveyDataException {
        String id = getInput("Enter your ID: ");

        List<Participant> allFromCSV = csvHandler.readCSV("participants_sample.csv");
        Participant participant = findParticipantById(allFromCSV, id);

        if (participant == null) {
            System.out.println("Participant not found.");
            return;
        }

        displayCurrentInfo(participant);

        System.out.println("\nWhat do you want to change?");
        System.out.println("1. Email");
        System.out.println("2. Preferred Game");
        System.out.println("3. Skill Level");
        System.out.println("4. Preferred Role");
        int choice = getUserInput("Choose (1–4): ",1, 4);

        String attributeName = getAttributeName(choice);
        if (attributeName == null) {
            System.out.println("Invalid choice.");
            return;
        }

        Object newValue = getNewAttributeValue(choice);

        TeamBuilder tempBuilder = new TeamBuilder(allFromCSV, currentTeamSize);
        tempBuilder.updateParticipantAttribute(participant, attributeName, newValue);

        csvHandler.exportUnassignedUser("participants_sample.csv", allFromCSV);
        System.out.println("Your info has been updated and saved to CSV.");

        if (tempBuilder.doesAttributeAffectBalance(attributeName) && teamBuilder != null && formedTeams != null) {
            System.out.println("Balance changed – re-forming teams...");
            allParticipants = allFromCSV;
            reformTeams();
        }
    }

    private static Participant findParticipantById(List<Participant> participants, String id) {
        for (Participant p : participants) {
            if (p.getId().equalsIgnoreCase(id)) {
                return p;
            }
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
        if (choice == 1) {
            return "email";
        } else if (choice == 2) {
            return "preferred game";
        } else if (choice == 3) {
            return "skill level";
        } else if (choice == 4) {
            return "preferred role";
        }
        return null;
    }

    private static Object getNewAttributeValue(int choice) {
        if (choice == 1) {
            return getValidEmail();
        } else if (choice == 2) {
            return getInput("New Preferred Game: ");
        } else if (choice == 3) {
            return getUserInput("New Skill Level (1–10): ",1, 10);
        } else if (choice == 4) {
            return getValidRole().name();
        }
        return null;
    }

    private static Team findTeamByParticipantId(String id) {
        if (formedTeams == null) {
            return null;
        }

        for (Team team : formedTeams) {
            if (team.containsParticipant(id) != null) {
                return team;
            }
        }

        return null;
    }

    // ============================= ORGANIZER MENU =============================
    private static void organizerMenu() {
        while (true) {
            System.out.println("\n--- Organizer Menu ---");
            System.out.println("1. Form Teams");
            System.out.println("2. View Teams");
            System.out.println("3. Remove Participant");
            System.out.println("4. Export Teams to CSV");
            System.out.println("5. Back");
            int c = getUserInput("Choose (1–5): ", 1, 5);
            if (c == 5) {
                break;
            }

            try {
                if (c == 1) {
                    formTeams();
                } else if (c == 2) {
                    viewTeams();
                } else if (c == 3) {
                    removeParticipant();
                } else if (c == 4) {
                    exportTeams();
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void formTeams() throws InvalidCSVFilePathException {
        String path;
        while (true) {
            System.out.print("Enter CSV file path : ");
            path = scanner.nextLine().trim();

            try {
                if (path.isEmpty()) {
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

        int teamSize = getUserInput("Enter desired team size (3-10, or 0 to use default): ",3, 10, true);
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

        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Participant> participants = csvHandler.readCSV(finalPath);
                    System.out.println("[Background] Loaded " + participants.size() + " participants.");

                    TeamBuilder builder = new TeamBuilder(participants, finalTeamSize);
                    List<Team> teams = builder.formTeams();

                    synchronized (Main.class) {
                        allParticipants = participants;
                        teamBuilder = builder;
                        formedTeams = teams;
                    }

                    System.out.println("[Background] Teams formed! " + teams.size() + " team(s) with size " + finalTeamSize + ".");
                    System.out.println(">>> TEAMS ARE READY! Use 'View Teams' to see them. <<<");
                } catch (Exception e) {
                    System.err.println("[Background] Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    private static void viewTeams() {
        if (teamBuilder == null || formedTeams == null) {
            System.out.println("Teams not formed yet. Choose 'Form Teams' first.");
            return;
        }
        teamBuilder.printTeams();
    }

    private static void removeParticipant() {
        if (teamBuilder == null || formedTeams==null) {
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
            System.out.println("Participant removed from list.");
            try {
                csvHandler.exportUnassignedUser("participants_sample.csv", allParticipants);
                System.out.println("CSV updated.");
            } catch (IOException e) {
                System.out.println("Warning: Could not update CSV: " + e.getMessage());
            }
            reformTeams();
        } else {
            System.out.println("Participant not found.");
        }
    }

    private static void exportTeams() {
        if (formedTeams == null) {
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
            csvHandler.toCSV(path, formedTeams);
            System.out.println("Exported to " + path);
        } catch (IOException e) {
            System.out.println("Export failed: " + e.getMessage());
        }
    }

    // ============================= HELPERS =============================
    private static void reformTeams() {
        if (allParticipants == null || allParticipants.isEmpty()) {
            System.out.println("No participants to form teams from.");
            return;
        }

        teamBuilder = new TeamBuilder(allParticipants, currentTeamSize);
        formedTeams = teamBuilder.formTeams();
        System.out.println("Teams re-formed successfully! " + formedTeams.size() + " team(s).");
    }

    private static String getInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static String getValidEmail() {
        while (true) {
            String email = getInput("Enter Email: ");
            if (email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
                return email;
            }
            System.out.println("Invalid email format.");
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

    private static int getUserInput(String prompt,int min, int max) {
        return getUserInput(prompt,min, max, false);
    }

    private static int getUserInput(String prompt, int min,int max, boolean allowZero) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = Integer.parseInt(scanner.nextLine().trim());
                if (allowZero) {
                    min = 0;
                }

                if (value >= min && value <= max) {
                    return value;
                }
                System.out.println("Enter a number between " + min + " and " + max);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Must be a number.");
            }
        }
    }
}