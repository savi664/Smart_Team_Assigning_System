package Service;

import Model.PersonalityType;
import Exception.InvalidSurveyDataException;

import java.util.Arrays;
import java.util.Scanner;

public class PersonalityClassifier {

    // Scanner for reading user input during survey
    Scanner scanner = new Scanner(System.in);

    // Calculates the overall personality score based on survey answers
    public int CalculatePersonalityScore() throws InvalidSurveyDataException {
        int[] answers = ConductSurvey(); // call Conductsurvey and get answers
        return Arrays.stream(answers).sum() * 4;
    }

    // Classifies personality type based on the calculated score
    public PersonalityType ClassifyPersonality(int score) {
        if (score >= 90) return PersonalityType.LEADER;
        else if (score >= 70) return PersonalityType.BALANCED;
        else if (score >= 50) return PersonalityType.THINKER;
        else return PersonalityType.SOCIALIZER;
    }

    // Conducts the personality survey by asking 5 questions
    private int[] ConductSurvey() throws InvalidSurveyDataException {
        int[] answers = new int[5]; // Store answers for each question
        String[] questions = {
                "Q1: I enjoy taking the lead and guiding others during group activities.",
                "Q2: I prefer analyzing situations and coming up with strategic solutions.",
                "Q3: I work well with others and enjoy collaborative teamwork.",
                "Q4: I am calm under pressure and can help maintain team morale.",
                "Q5: I like making quick decisions and adapting in dynamic situations."
        };

        for (int i = 0; i < questions.length; i++) {
            System.out.print(questions[i] + " (Enter a number 1-5): ");

            // Validate input is an integer
            if (!scanner.hasNextInt()) {
                throw new InvalidSurveyDataException("Invalid input. Must be an integer between 1 and 5.");
            }
            int answer = scanner.nextInt();
            scanner.nextLine();

            // Validate the answer is within range
            if (answer < 1 || answer > 5) {
                throw new InvalidSurveyDataException("Invalid answer for Q" + (i + 1) + ". Must be 1-5.");
            }

            answers[i] = answer;
        }

        return answers; // Return all validated survey answers
    }

}
