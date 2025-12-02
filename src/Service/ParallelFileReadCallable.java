package Service;

import Model.Participant;
import Utility.Logger;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ParallelFileReadCallable implements Callable<List<Participant>> {

    private final String filePath;
    private final ExecutorService executor;
    private final int numThreads;
    private final CSVHandler csvHandler = new CSVHandler()    ;

    public ParallelFileReadCallable(String filePath, ExecutorService executor, int numThreads) {
        this.filePath = filePath;
        this.executor = executor;
        this.numThreads = numThreads;
    }

    @Override
    public List<Participant> call() throws Exception {
        Logger.info("ParallelFileReadCallable: Reading CSV in parallel from: " + filePath);

        List<String> allLines = Files.readAllLines(new File(filePath).toPath());
        if (allLines.size() <= 1) return new ArrayList<>(); // header only or empty

        // Remove header
        allLines.removeFirst();

        int totalLines = allLines.size();
        int chunkSize = (int) Math.ceil((double) totalLines / numThreads);

        List<Future<List<Participant>>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            int start = i * chunkSize;
            int end = Math.min((i + 1) * chunkSize, totalLines);
            List<String> subList = allLines.subList(start, end);

            futures.add(executor.submit(() -> {
                List<Participant> chunkParticipants = new ArrayList<>();
                for (String line : subList) {
                    chunkParticipants.add(csvHandler.parseLineToParticipant(line));
                }
                return chunkParticipants;
            }));
        }

        // Merge results
        List<Participant> participants = new ArrayList<>();
        for (Future<List<Participant>> f : futures) {
            participants.addAll(f.get());
        }

        Logger.info("ParallelFileReadCallable: Completed reading " + participants.size() + " participants");
        return participants;
    }
}
