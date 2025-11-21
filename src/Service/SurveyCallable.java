package Service;

import Model.Participant;
import Model.PersonalityType;
import Model.RoleType;
import Utility.Logger;

import java.util.concurrent.Callable;

public class SurveyCallable implements Callable<Participant> {

    private final String id;
    private final String name;
    private final String email;
    private final String game;
    private final int skill;
    private final RoleType role;
    private final PersonalityClassifier classifier;

    public SurveyCallable(String id, String name, String email, String game,
                          int skill, RoleType role, PersonalityClassifier classifier) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.game = game;
        this.skill = skill;
        this.role = role;
        this.classifier = classifier;
    }

    @Override
    public Participant call() throws Exception {
        Logger.info("Survey execution started in thread pool for participant: " + id);

        // Conduct survey
        int[] answers = classifier.ConductSurvey();
        Logger.debug("Survey answers collected for participant: " + id);

        // Calculate personality score
        int score = classifier.CalculatePersonalityScore(answers);
        Logger.debug("Personality score calculated: " + score);

        // Classify personality type
        PersonalityType type = classifier.ClassifyPersonality(score);
        Logger.debug("Personality type classified: " + type);

        // Create and return participant
        Participant participant = new Participant(id, name, email, game, skill, role, score, type);
        Logger.info("Survey completed - Participant: " + id + ", Score: " + score + ", Type: " + type);

        return participant;
    }
}