package Service;

import Model.Participant;
import Exception.InvalidSurveyDataException;
import Utility.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

public class FileReadCallable implements Callable<List<Participant>> {

    private final String filePath;

    public FileReadCallable(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public List<Participant> call() throws IOException, InvalidSurveyDataException {
        Logger.info("FileReadCallable: Starting to read CSV from: " + filePath);

        try {
            List<Participant> participants = CSVHandler.readCSV(filePath);
            Logger.info("FileReadCallable: Successfully loaded " + participants.size() + " participants");
            return participants;
        } catch (IOException e) {
            Logger.error("FileReadCallable: IOException while reading file: " + e.getMessage());
            throw e;
        } catch (InvalidSurveyDataException e) {
            Logger.error("FileReadCallable: InvalidSurveyDataException: " + e.getMessage());
            throw e;
        }
    }
}
