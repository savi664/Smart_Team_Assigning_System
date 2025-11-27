package Service;
import Model.PersonalityType;
import Exception.InvalidSurveyDataException;

import java.util.Arrays;
import java.util.Scanner;


public class PersonalityClassifier {
    Scanner scanner = new Scanner(System.in);
    public int CalculatePersonalityScore() throws InvalidSurveyDataException {
        int[] answers = ConductSurvey();
        return Arrays.stream(answers).sum() * 4; // Maps 5–25 to 20–100
    }

    public PersonalityType ClassifyPersonality(int score) {
        if (score >= 80) return PersonalityType.LEADER; // Adjusted from 90
        else if (score >= 60) return PersonalityType.BALANCED; // Adjusted from 70
        else if (score >= 40) return PersonalityType.THINKER; // Adjusted from 50
        else return PersonalityType.SOCIALIZER;
    }

    private int[] ConductSurvey() throws InvalidSurveyDataException {
        int[] answers = new int[5];
        String[] questions= {
                "Q1: I enjoy taking the lead and guiding others during group activities.",
                "Q2: I prefer analyzing situations and coming up with strategic solutions.",
                "Q3: I work well with others and enjoy collaborative teamwork.",
                "Q4: I am calm under pressure and can help maintain team morale.",
                "Q5: I like making quick decisions and adapting in dynamic situations."
        };

        for (int i = 0; i < questions.length; i++) {
            System.out.print(questions[i] + " (Enter a number 1-5): ");
            if (!scanner.hasNextInt()) {
                throw new InvalidSurveyDataException("Invalid input. Must be an integer between 1 and 5.");
            }
            int answer = scanner.nextInt();
            scanner.nextLine();
            if (answer < 1 || answer > 5) {
                throw new InvalidSurveyDataException("Invalid answer for Q" + (i + 1) + ". Must be 1-5.");
            }
            answers[i] = answer;
        }

        return answers;
    }

}
