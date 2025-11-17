package Service;

import Model.*;
import Exception.SkillLevelOutOfBoundsException;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TeamBuilder {
    private final List<Participant> participantPool;
    private final List<Team> compliantTeams = new ArrayList<>();
    private final List<Team> overflowTeams = new ArrayList<>();
    private final int teamSize;
    private int nextTeamId = 1;

    private static final int MAX_SAME_GAME = 2;
    private static final int MAX_THINKERS = 2;
    private static final int MAX_LEADERS = 1;
    private static final int MAX_SOCIALIZERS = 1;
    private static final int MIN_ROLE_DIVERSITY = 3;

    public TeamBuilder(List<Participant> participants, int teamSize) {
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Participants list cannot be empty");
        }
        if (teamSize < 2 || teamSize > 10) {
            throw new IllegalArgumentException("Team size must be between 2 and 10");
        }
        this.teamSize = teamSize;
        this.participantPool = new ArrayList<>(participants);
        Collections.shuffle(this.participantPool);
    }

    public List<Team> getTeams() {
        List<Team> allTeams = new ArrayList<>();
        allTeams.addAll(compliantTeams);
        allTeams.addAll(overflowTeams);
        return allTeams;
    }

    public List<Team> formTeams() {
        compliantTeams.clear();
        overflowTeams.clear();

        List<Participant> remaining = new ArrayList<>(participantPool);
        sortParticipantsByPriority(remaining);

        // Phase 1: Form constraint-compliant teams with EXACTLY teamSize members
        while (remaining.size() >= teamSize && canFormCompliantTeam(remaining)) {
            Team team = new Team(nextTeamId++);
            List<Participant> teamMembers = selectTeamMembers(remaining);

            // Only add if we got exactly teamSize members
            if (teamMembers.size() == teamSize) {
                for (Participant p : teamMembers) {
                    team.addMember(p);
                    remaining.remove(p);
                }
                compliantTeams.add(team);
            } else {
                // Can't form a compliant team anymore
                break;
            }
        }

        // Phase 2: Put ALL remaining participants into overflow team(s)
        if (!remaining.isEmpty()) {
            createOverflowTeams(remaining);
        }

        // Balance skills across compliant teams only
        balanceTeamSkills(compliantTeams);

        return getTeams();
    }

    private void createOverflowTeams(List<Participant> remaining) {
        Collections.shuffle(remaining);

        // Create teams with remaining participants
        while (!remaining.isEmpty()) {
            Team overflowTeam = new Team(nextTeamId++);

            // Take up to teamSize participants, or all remaining if less
            int count = Math.min(teamSize, remaining.size());
            for (int i = 0; i < count; i++) {
                overflowTeam.addMember(remaining.getFirst());
                remaining.removeFirst();
            }

            overflowTeams.add(overflowTeam);
        }
    }

    private boolean canFormCompliantTeam(List<Participant> available) {
        if (available.size() < teamSize) {
            return false;
        }

        // Check if we have at least one leader
        boolean hasLeader = available.stream()
                .anyMatch(p -> p.getPersonalityType() == PersonalityType.LEADER);

        if (!hasLeader) {
            return false;
        }

        // Check if we can satisfy role diversity
        Set<RoleType> availableRoles = new HashSet<>();
        for (Participant p : available) {
            availableRoles.add(p.getPreferredRole());
        }

        return availableRoles.size() >= MIN_ROLE_DIVERSITY;
    }

    private List<Participant> selectTeamMembers(List<Participant> available) {
        List<Participant> selected = new ArrayList<>();
        List<Participant> candidates = new ArrayList<>(available);

        // Step 1: Select one leader first
        Participant leader = candidates.stream()
                .filter(p -> p.getPersonalityType() == PersonalityType.LEADER)
                .findFirst()
                .orElse(null);

        if (leader != null) {
            selected.add(leader);
            candidates.remove(leader);
        }

        // Step 2: Fill remaining slots while respecting constraints
        while (selected.size() < teamSize && !candidates.isEmpty()) {
            Participant best = findBestCandidateForTeam(selected, candidates);

            if (best != null) {
                selected.add(best);
                candidates.remove(best);
            } else {
                // Can't form a complete compliant team
                return selected;
            }
        }

        return selected;
    }

    private Participant findBestCandidateForTeam(List<Participant> currentMembers, List<Participant> candidates) {
        Participant bestCandidate = null;
        double bestScore = -1;

        for (Participant candidate : candidates) {
            if (wouldViolateConstraints(currentMembers, candidate)) {
                continue;
            }

            double score = calculateCandidateScore(currentMembers, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }

        return bestCandidate;
    }

    private boolean wouldViolateConstraints(List<Participant> currentMembers, Participant candidate) {
        // Check game constraint
        long sameGameCount = currentMembers.stream()
                .filter(p -> p.getPreferredGame().equals(candidate.getPreferredGame()))
                .count();
        if (sameGameCount >= MAX_SAME_GAME) {
            return true;
        }

        // Check personality constraint
        PersonalityType candidateType = candidate.getPersonalityType();
        long personalityCount = currentMembers.stream()
                .filter(p -> p.getPersonalityType() == candidateType)
                .count();

        if (candidateType == PersonalityType.LEADER && personalityCount >= MAX_LEADERS) {
            return true;
        }
        if (candidateType == PersonalityType.THINKER && personalityCount >= MAX_THINKERS) {
            return true;
        }
        if (candidateType == PersonalityType.SOCIALIZER && personalityCount >= MAX_SOCIALIZERS) {
            return true;
        }

        return false;
    }

    private double calculateCandidateScore(List<Participant> currentMembers, Participant candidate) {
        double score = 0.0;

        // Prefer role diversity
        Set<RoleType> existingRoles = new HashSet<>();
        for (Participant p : currentMembers) {
            existingRoles.add(p.getPreferredRole());
        }
        if (!existingRoles.contains(candidate.getPreferredRole())) {
            score += 3.0;
        }

        // Prefer skill balance
        if (!currentMembers.isEmpty()) {
            double avgSkill = currentMembers.stream()
                    .mapToInt(Participant::getSkillLevel)
                    .average()
                    .orElse(5.0);
            double skillDiff = Math.abs(candidate.getSkillLevel() - avgSkill);
            score += (3.0 - Math.min(skillDiff, 3.0));
        }

        // Prefer thinkers if we don't have 2 yet
        long thinkerCount = currentMembers.stream()
                .filter(p -> p.getPersonalityType() == PersonalityType.THINKER)
                .count();
        if (candidate.getPersonalityType() == PersonalityType.THINKER && thinkerCount < 2) {
            score += 2.0;
        }

        return score;
    }

    private void sortParticipantsByPriority(List<Participant> participants) {
        // Sort by: Leaders first, then by skill level descending
        participants.sort((p1, p2) -> {
            if (p1.getPersonalityType() == PersonalityType.LEADER &&
                    p2.getPersonalityType() != PersonalityType.LEADER) {
                return -1;
            }
            if (p2.getPersonalityType() == PersonalityType.LEADER &&
                    p1.getPersonalityType() != PersonalityType.LEADER) {
                return 1;
            }
            return Integer.compare(p2.getSkillLevel(), p1.getSkillLevel());
        });
    }

    public void updateParticipantAttribute(Participant participant, String attributeName, Object newValue)
            throws SkillLevelOutOfBoundsException {

        if (participant == null) {
            throw new IllegalArgumentException("Participant cannot be null");
        }

        String attribute = attributeName.toLowerCase().trim();

        switch (attribute) {
            case "email" -> updateEmail(participant, newValue);
            case "preferred game" -> updateGame(participant, newValue);
            case "skill level" -> updateSkillLevel(participant, newValue);
            case "preferred role" -> updateRole(participant, newValue);
            default -> throw new IllegalArgumentException(
                    "Invalid attribute '" + attributeName + "'. Valid options: email, preferred game, skill level, preferred role");
        }
    }

    public boolean doesAttributeAffectBalance(String attributeName) {
        String attribute = attributeName.toLowerCase().trim();
        return switch (attribute) {
            case "preferred game", "skill level", "preferred role" -> true;
            default -> false;
        };
    }

    public void printTeams() {
        if (compliantTeams.isEmpty() && overflowTeams.isEmpty()) {
            System.out.println("No teams formed yet.");
            return;
        }

        System.out.println("\n==========================");
        System.out.println("   FORMED TEAMS");
        System.out.println("==========================\n");

        // Print all compliant teams
        for (Team team : compliantTeams) {
            printSingleTeam(team, false);
        }

        // Print overflow teams with warning
        if (!overflowTeams.isEmpty()) {
            System.out.println("\n--- Note: The following teams do not satisfy all constraints ---");
            System.out.println("    (Insufficient participants remaining after forming balanced teams)\n");

            for (Team team : overflowTeams) {
                printSingleTeam(team, true);
            }
        }

        printSummary();
    }

    private void printSingleTeam(Team team, boolean isOverflowTeam) {
        System.out.println("==========================");
        System.out.println(" Team " + team.getTeam_id());
        System.out.println("==========================");

        List<Participant> members = team.getParticipantList();
        for (Participant participant : members) {
            System.out.printf(
                    "ID: %-5s | Name: %-15s | Game: %-10s | Skill: %-2d | Role: %-11s | Type: %-10s%n",
                    participant.getId(),
                    truncate(participant.getName()),
                    participant.getPreferredGame(),
                    participant.getSkillLevel(),
                    participant.getPreferredRole(),
                    participant.getPersonalityType()
            );
        }

        System.out.printf("Team Avg Skill: %.2f%n", team.CalculateAvgSkill());
        System.out.printf("Team Size: %d/%d%n", members.size(), teamSize);

        if (isOverflowTeam) {
            printViolations(team);
        }

        System.out.println();
    }

    private void printViolations(Team team) {
        List<String> violationList = new ArrayList<>();
        List<Participant> members = team.getParticipantList();

        // Check if team size is wrong
        int currentSize = members.size();
        if (currentSize != teamSize) {
            violationList.add("Incomplete team (" + currentSize + "/" + teamSize + ")");
        }
        // Check for too many players from same game
        Map<String, Integer> gameCount = new HashMap<>();
        for (Participant participant : members) {
            String game = participant.getPreferredGame();
            if (gameCount.containsKey(game)) {
                gameCount.put(game, gameCount.get(game) + 1);
            } else {
                gameCount.put(game, 1);
            }
        }

        for (String game : gameCount.keySet()) {
            int count = gameCount.get(game);
            if (count > MAX_SAME_GAME) {
                violationList.add("Too many " + game + " players (" + count + ")");
            }
        }

        // Check personality types
        int leaderCount = 0;
        int thinkerCount = 0;
        int socializerCount = 0;

        for (Participant participant : members) {
            PersonalityType personality = participant.getPersonalityType();
            if (personality == PersonalityType.LEADER) {
                leaderCount++;
            } else if (personality == PersonalityType.THINKER) {
                thinkerCount++;
            } else if (personality == PersonalityType.SOCIALIZER) {
                socializerCount++;
            }
        }

        if (leaderCount == 0) {
            violationList.add("No leader");
        } else if (leaderCount > MAX_LEADERS) {
            violationList.add("Too many leaders (" + leaderCount + ")");
        }

        if (thinkerCount > MAX_THINKERS) {
            violationList.add("Too many thinkers (" + thinkerCount + ")");
        }

        if (socializerCount > MAX_SOCIALIZERS) {
            violationList.add("Too many socializers (" + socializerCount + ")");
        }

        // Check role diversity
        Set<RoleType> uniqueRoles = new HashSet<>();
        for (Participant participant : members) {
            uniqueRoles.add(participant.getPreferredRole());
        }

        int roleCount = uniqueRoles.size();
        if (roleCount < MIN_ROLE_DIVERSITY) {
            violationList.add("Not enough role variety (" + roleCount + "/" + MIN_ROLE_DIVERSITY + ")");
        }

        // Print violations if any exist
        if (!violationList.isEmpty()) {
            System.out.print("Violations: ");
            for (int i = 0; i < violationList.size(); i++) {
                System.out.print(violationList.get(i));
                if (i < violationList.size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println();
        }
    }

    private void printSummary() {
        List<Team> allTeams = getTeams();
        double totalSkillSum = 0;
        int totalParticipantCount = 0;

        for (Team team : allTeams) {
            List<Participant> members = team.getParticipantList();
            for (Participant participant : members) {
                totalSkillSum += participant.getSkillLevel();
                totalParticipantCount++;
            }
        }

        double overallAvgSkill = 0;
        if (totalParticipantCount > 0) {
            overallAvgSkill = totalSkillSum / totalParticipantCount;
        }

        System.out.println("==========================");
        System.out.println("       SUMMARY");
        System.out.println("==========================");
        System.out.printf("Total Teams: %d%n", allTeams.size());
        System.out.printf("Balanced Teams: %d%n", compliantTeams.size());
        System.out.printf("Overflow Teams: %d%n", overflowTeams.size());
        System.out.printf("Total Participants: %d%n", totalParticipantCount);
        System.out.printf("Global Average Skill: %.2f%n", overallAvgSkill);
        System.out.println("==========================\n");
    }

    private String truncate(String str) {
        if (str == null) return "";
        return str.length() > 15 ? str.substring(0, 15 - 2) + ".." : str;
    }

    public Team findFittingTeam(Participant participant) {
        // Try compliant teams first - only if they're not full
        for (Team team : compliantTeams) {
            if (team.getParticipantList().size() < teamSize &&
                    !wouldViolateConstraints(team.getParticipantList(), participant)) {
                return team;
            }
        }

        // Try overflow teams
        for (Team team : overflowTeams) {
            if (team.getParticipantList().size() < teamSize) {
                return team;
            }
        }

        // Create new overflow team if all are full
        Team newOverflowTeam = new Team(nextTeamId++);
        overflowTeams.add(newOverflowTeam);
        return newOverflowTeam;
    }

    private void balanceTeamSkills(List<Team> teams) {
        if (teams.size() <= 1) {
            return;
        }

        for (int iteration = 0; iteration < 30; iteration++) {
            teams.sort(Comparator.comparingDouble(Team::CalculateAvgSkill));

            Team weakestTeam = teams.getFirst();
            Team strongestTeam = teams.getLast();

            double skillGap = strongestTeam.CalculateAvgSkill() - weakestTeam.CalculateAvgSkill();
            if (skillGap < 1.0) {
                break;
            }

            if (trySwapBetweenTeams(strongestTeam, weakestTeam)) {
                continue;
            }

            break;
        }
    }

    private boolean trySwapBetweenTeams(Team team1, Team team2) {
        List<Participant> team1Members = new ArrayList<>(team1.getParticipantList());
        List<Participant> team2Members = new ArrayList<>(team2.getParticipantList());

        for (Participant p1 : team1Members) {
            for (Participant p2 : team2Members) {
                team1.removeMember(p1);
                team2.removeMember(p2);

                boolean canSwap = !wouldViolateConstraints(team1.getParticipantList(), p2) &&
                        !wouldViolateConstraints(team2.getParticipantList(), p1);

                if (canSwap) {
                    double oldGap = Math.abs(
                            team1.CalculateAvgSkill() - team2.CalculateAvgSkill()
                    );

                    team1.addMember(p2);
                    team2.addMember(p1);

                    double newGap = Math.abs(
                            team1.CalculateAvgSkill() - team2.CalculateAvgSkill()
                    );

                    if (newGap < oldGap) {
                        return true;
                    }

                    // Revert
                    team1.removeMember(p2);
                    team2.removeMember(p1);
                    team1.addMember(p1);
                    team2.addMember(p2);
                } else {
                    team1.addMember(p1);
                    team2.addMember(p2);
                }
            }
        }

        return false;
    }

    // ==================== ATTRIBUTE UPDATE METHODS ====================

    private void updateEmail(Participant participant, Object value) {
        if (!(value instanceof String email)) {
            throw new IllegalArgumentException("Email must be a String");
        }

        Pattern emailPattern = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$");

        if (!emailPattern.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format. Use format: example@domain.com");
        }

        participant.setEmail(email);
    }

    private void updateGame(Participant participant, Object value) {
        if (!(value instanceof String game)) {
            throw new IllegalArgumentException("Game must be a String");
        }

        participant.setPreferredGame(game);
    }

    private void updateSkillLevel(Participant participant, Object value) throws SkillLevelOutOfBoundsException {
        if (!(value instanceof Integer skillLevel)) {
            throw new IllegalArgumentException("Skill level must be an Integer");
        }

        if (skillLevel < 1 || skillLevel > 10) {
            throw new SkillLevelOutOfBoundsException("Skill level must be between 1 and 10");
        }

        participant.setSkillLevel(skillLevel);
    }

    private void updateRole(Participant participant, Object value) {
        if (!(value instanceof String roleString)) {
            throw new IllegalArgumentException("Role must be a String");
        }

        try {
            RoleType role = RoleType.valueOf(roleString.trim().toUpperCase());
            participant.setPreferredRole(role);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid role. Valid options: STRATEGIST, ATTACKER, DEFENDER, SUPPORTER, COORDINATOR");
        }
    }
}