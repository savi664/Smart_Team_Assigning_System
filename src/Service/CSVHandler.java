// File: Service/CSVHandler.java
package Service;

import Exception.InvalidSurveyDataException;
import Model.Participant;
import Model.PersonalityType;
import Model.RoleType;
import Model.Team;
import Utility.CSVService;
import Exception.InvalidCSVFilePathException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class CSVHandler implements CSVService {

    private static final String PARTICIPANTS_FILE = "participants_sample.csv"; // main storage file

    @Override
    public boolean containsID(String id) throws InvalidSurveyDataException, IOException {
        return readCSV(PARTICIPANTS_FILE).stream()
                .anyMatch(p -> p.getId().equals(id)); // quick existence check without loading full structure
    }

    @Override
    public List<Participant> readCSV(String path) throws IOException, InvalidSurveyDataException {
        List<Participant> list = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue; // ignore spacing / empty rows
                list.add(parseLineToParticipant(line)); // centralised validation + mapping
            }
        }
        return list;
    }

    @Override
    public void toCSV(String path, List<Team> teams) throws IOException, InvalidCSVFilePathException {
        if (teams == null || teams.isEmpty()) {
            throw new IllegalArgumentException("No teams to export.");
        }

        if (!path.endsWith(".csv")) {
            throw new InvalidCSVFilePathException("CSV files must end with '.csv'."); // avoids accidental mis-naming
        }

        File file = new File(path);
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Failed to create file: " + file.getAbsolutePath()); // safety net for write failures
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.newLine(); // spacing before first team block

            for (int i = 0; i < teams.size(); i++) {
                Team team = teams.get(i);

                // team block header — improves readability when opened manually
                writer.write("# ==============================\n");
                writer.write("# TEAM " + team.getTeam_id() +
                        " (Size: " + team.getParticipantList().size() +
                        ", Avg Skill: " + String.format("%.2f", team.CalculateAvgSkill()) + ")\n");
                writer.write("# ==============================\n");

                writer.write("TeamID,ID,Name,Email,PreferredGame,SkillLevel,Role,PersonalityScore,PersonalityType\n");

                for (Participant p : team.getParticipantList()) {
                    writeParticipantWithTeam(writer, team.getTeam_id(), p); // shared formatting logic
                }

                if (i < teams.size() - 1) {
                    writer.newLine(); // spacing between team sections
                }
            }
            System.out.println("Exported successfully to: " + file.getAbsolutePath());
        }
    }

    @Override
    public void exportUnassignedUser(String path, List<Participant> participants) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write("ID,Name,Email,PreferredGame,SkillLevel,Role,PersonalityScore,PersonalityType\n");
            for (Participant p : participants) {
                writeParticipantNoTeam(writer, p); // separate format for "unassigned" output
            }
        }
    }

    @Override
    public void addToCSV(Participant p) throws IOException {
        // append-only write — assuming participants_sample.csv is the master dataset
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PARTICIPANTS_FILE, true))) {
            writer.write(String.join(",",
                    escapeCSV(p.getId()),
                    escapeCSV(p.getName()),
                    escapeCSV(p.getEmail()),
                    escapeCSV(p.getPreferredGame()),
                    String.valueOf(p.getSkillLevel()),
                    escapeCSV(p.getPreferredRole().name()),
                    String.valueOf(p.getPersonalityScore()),
                    escapeCSV(p.getPersonalityType().name())
            ));
            writer.newLine();
        }
    }

    @Override
    public Participant parseLineToParticipant(String line) throws InvalidSurveyDataException {
        String[] values = line.split(",");

        if (values.length < 8) {
            // ensures bad rows fail fast instead of silently polluting the list
            throw new InvalidSurveyDataException("Invalid CSV row (expected 8 columns): " + line);
        }

        try {
            return new Participant(
                    values[0].trim(),
                    values[1].trim(),
                    values[2].trim(),
                    values[3].trim(),
                    Integer.parseInt(values[4].trim()),
                    RoleType.valueOf(values[5].trim().toUpperCase()), // using upper case to ensure simple case safety
                    Integer.parseInt(values[6].trim()),
                    PersonalityType.valueOf(values[7].trim().toUpperCase())
            );
        } catch (NumberFormatException e) {
            throw new InvalidSurveyDataException("Invalid number format: " + line); // detects corrupted numeric fields
        } catch (IllegalArgumentException e) {
            throw new InvalidSurveyDataException("Invalid enum value: " + line); // catches bad role/personality strings
        }
    }

    // CSV output helper when a team ID is required
    private void writeParticipantWithTeam(BufferedWriter writer, int teamId, Participant p) throws IOException {
        writer.write(String.join(",",
                String.valueOf(teamId),
                escapeCSV(p.getId()),
                escapeCSV(p.getName()),
                escapeCSV(p.getEmail()),
                escapeCSV(p.getPreferredGame()),
                String.valueOf(p.getSkillLevel()),
                escapeCSV(p.getPreferredRole().name()),
                String.valueOf(p.getPersonalityScore()),
                escapeCSV(p.getPersonalityType().name())
        ));
        writer.newLine();
    }

    // CSV output for unassigned users — intentionally omits team column
    private void writeParticipantNoTeam(BufferedWriter writer, Participant p) throws IOException {
        writer.write(String.join(",",
                escapeCSV(p.getId()),
                escapeCSV(p.getName()),
                escapeCSV(p.getEmail()),
                escapeCSV(p.getPreferredGame()),
                String.valueOf(p.getSkillLevel()),
                escapeCSV(p.getPreferredRole().name()),
                String.valueOf(p.getPersonalityScore()),
                escapeCSV(p.getPersonalityType().name())
        ));
        writer.newLine();
    }

    // protects CSV fields from breaking structure if they contain commas/quotes/newlines
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""; // minimal RFC-compliant escaping
        }
        return value;
    }
}
