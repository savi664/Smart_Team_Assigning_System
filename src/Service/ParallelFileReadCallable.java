package Service;

import Exception.InvalidSurveyDataException;
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
    private final CSVHandler csvHandler = new CSVHandler(); // used by each worker to parse lines

    public ParallelFileReadCallable(String filePath, ExecutorService executor, int numThreads) {
        this.filePath = filePath;
        this.executor = executor;
        this.numThreads = numThreads;
    }

    @Override
    public List<Participant> call() throws Exception {
        Logger.info("ParallelFileReadCallable: Reading CSV in parallel from: " + filePath);

        List<String> allLines = Files.readAllLines(new File(filePath).toPath());
        if (allLines.size() <= 1) return new ArrayList<>(); // no data beyond header

        allLines.removeFirst(); // drop header before partitioning

        int totalLines = allLines.size();
        int chunkSize = (int) Math.ceil((double) totalLines / numThreads); // balanced chunk size per thread

        List<Future<List<Participant>>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        Logger.info("START: ParallelFileReadCallable with " + numThreads + " threads");

        for (int i = 0; i < numThreads; i++) {
            int threadNum = i; // keep stable thread ID for better logging info
            int start = i * chunkSize;
            int end = Math.min((i + 1) * chunkSize, totalLines);
            List<String> subList = allLines.subList(start, end); // slice assigned to this worker

            // Each submitted task parses its own chunk of raw CSV lines
            futures.add(executor.submit(() -> {
                Logger.info("Thread-" + threadNum + " processing lines " + start + " to " + end);

                List<Participant> chunkParticipants = new ArrayList<>();

                try {
                    for (String line : subList) {
                        chunkParticipants.add(csvHandler.parseLineToParticipant(line)); // shared parser enforces strict validation
                    }
                } catch (InvalidSurveyDataException e) {
                    // surfaces parsing issues without killing all threads
                    Logger.error("CSV parsing error: " + e.getMessage());
                    System.err.println("\nCSV parsing error: " + e.getMessage());
                }

                Logger.info("Thread-" + threadNum + " completed");
                return chunkParticipants;
            }));
        }

        // Collect results from all worker tasks
        List<Participant> participants = new ArrayList<>();
        for (Future<List<Participant>> f : futures) {
            participants.addAll(f.get()); // safe because each thread returns its isolated list
        }

        long endTime = System.currentTimeMillis();
        Logger.info("COMPLETED: CSV read in " + (endTime - startTime) + "ms");

        return participants;
    }
}
