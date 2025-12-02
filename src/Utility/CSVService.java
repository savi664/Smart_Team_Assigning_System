// Service/CSVService.java
package Utility;

import Exception.InvalidSurveyDataException;
import Model.Participant;
import Model.Team;
import Exception.InvalidCSVFilePathException;
import java.io.IOException;
import java.util.List;

public interface CSVService {
    boolean containsID(String id) throws InvalidSurveyDataException, IOException;
    List<Participant> readCSV(String path) throws IOException, InvalidSurveyDataException;
    void toCSV(String path, List<Team> teams) throws IOException, InvalidCSVFilePathException;
    void exportUnassignedUser(String path, List<Participant> participants) throws IOException;
    void addToCSV(Participant p) throws IOException;
    Participant parseLineToParticipant(String line) throws InvalidSurveyDataException;
}