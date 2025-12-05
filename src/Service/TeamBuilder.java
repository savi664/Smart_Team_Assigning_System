package Service;

import Model.*;
import Utility.Logger;

import java.util.*;
import java.util.concurrent.*;

public class TeamBuilder {

    // Full list of participants provided from outside
    private final List<Participant> allParticipants;

    // Teams that fully satisfy rules
    private final List<Team> balancedTeams = new ArrayList<>();

    // Teams created with leftovers (might break rules)
    private final List<Team> overflowTeams = new ArrayList<>();

    private final int targetTeamSize; // Requested team size
    private int nextTeamId = 1;       // Auto incremental team ID
    private final ExecutorService executor;

    // RULE LIMITS (the constraints the algorithm must respect)
    private static final int MAX_SAME_GAME = 2;
    private static final int MAX_LEADERS = 1;
    private static final int MAX_THINKERS = 2;
    private static final int MAX_SOCIALIZERS = 1;
    private static final int MIN_DIFFERENT_ROLES = 3;

    // Settings for when to parallelize the selection process
    private static final int PARALLEL_THRESHOLD = 150;
    private static final int MIN_CHUNK_SIZE = 25;

    // Main constructor
    public TeamBuilder(List<Participant> participants, int teamSize) {
        this(participants, teamSize, null);
    }

    // Overloaded constructor with optional executor for parallel processing
    public TeamBuilder(List<Participant> participants, int teamSize, ExecutorService executor) {
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("No participants given!");
        }
        if (teamSize < 2 || teamSize > 10) {
            throw new IllegalArgumentException("Team size must be 2–10");
        }
        this.allParticipants = new ArrayList<>(participants); // Copy for safety
        this.targetTeamSize = teamSize;
        this.executor = executor;
    }

    // Main function to form all teams
    public List<Team> formTeams() {
        balancedTeams.clear();
        overflowTeams.clear();
        nextTeamId = 1;

        List<Participant> remaining = new ArrayList<>(allParticipants);

        // Push leaders to the front so each team has a chance to get one
        putLeadersFirst(remaining);

        // Shuffle to avoid predictable grouping
        Collections.shuffle(remaining);

        Logger.info("=".repeat(60));
        Logger.info("TEAM FORMATION START: " + remaining.size() + " participants");
        Logger.info("Mode: " + (allParticipants.size() >= 150 ? "PARALLEL ENABLED" : "SEQUENTIAL ONLY"));
        Logger.info("=".repeat(60));

        // Keep forming full teams while possible
        while (remaining.size() >= targetTeamSize) {
            Team team = tryMakeCompliantTeam(remaining);
            if (team != null && team.getParticipantList().size() == targetTeamSize) {
                balancedTeams.add(team);
                remaining.removeAll(team.getParticipantList());
            } else {
                break; // If we fail once, remaining can't form a balanced team
            }
        }

        // Anything left over becomes overflow teams
        if (!remaining.isEmpty()) {
            makeOverflowTeams(remaining);
        }

        // Skill-balance teams by swapping players
        balanceSkills(balancedTeams);

        // Summary
        Logger.info("=".repeat(60));
        Logger.info("TEAM FORMATION COMPLETE");
        Logger.info("Teams formed: " + getAllTeams().size());
        Logger.info("=".repeat(60));

        return getAllTeams();
    }

    // Moves all leaders to top of list to ensure leader-first team formation
    private void putLeadersFirst(List<Participant> list) {
        list.sort((a, b) -> {
            if (a.getPersonalityType() == PersonalityType.LEADER) return -1;
            if (b.getPersonalityType() == PersonalityType.LEADER) return 1;
            return 0;
        });
    }

    // Attempts to build a rule-compliant single team
    private Team tryMakeCompliantTeam(List<Participant> pool) {
        List<Participant> available = new ArrayList<>(pool);
        Team team = new Team(nextTeamId++);
        List<Participant> chosen = new ArrayList<>();

        // Rule: team must have exactly one leader
        Participant leader = null;
        for (Participant participant : available) {
            if (participant.getPersonalityType() == PersonalityType.LEADER) {
                leader = participant;
                break;
            }
        }
        if (leader == null) return null; // Can't form a legal team

        chosen.add(leader);
        available.remove(leader);

        // Add remaining best players using scoring logic
        while (chosen.size() < targetTeamSize && !available.isEmpty()) {
            Participant best = findBestPlayer(chosen, available);
            if (best == null) break; // No valid candidate found
            chosen.add(best);
            available.remove(best);
        }

        // Final validation
        if (chosen.size() == targetTeamSize && hasEnoughRoles(chosen)) {
            for (Participant participant : chosen) {
                team.addMember(participant);
            }
            return team;
        }
        return null;
    }

    // Decides whether the "best player" search should run parallel or sequential
    private Participant findBestPlayer(List<Participant> team, List<Participant> candidates) {
        boolean shouldUseParallel = executor != null && candidates.size() >= PARALLEL_THRESHOLD;

        // For debugging/logging: only for first few teams
        if (shouldUseParallel) {
            if (balancedTeams.size() < 3) {
                int numThreads = Math.min(Runtime.getRuntime().availableProcessors(),
                        candidates.size() / MIN_CHUNK_SIZE);
                int chunkSize = (int) Math.ceil((double) candidates.size() / numThreads);
                Logger.info(String.format("Team %d: %d candidates → %d threads (~%d per chunk)",
                        nextTeamId - 1, candidates.size(), numThreads, chunkSize));
            }
            return findBestPlayerParallel(team, candidates);
        } else {
            return findBestPlayerSequential(team, candidates);
        }
    }

    // Standard sequential scanning for best-fit candidate
    private Participant findBestPlayerSequential(List<Participant> team, List<Participant> candidates) {
        Participant best = null;
        double bestScore = -1;

        for (Participant candidate : candidates) {
            if (breaksRules(team, candidate)) continue;
            double score = calculateScore(team, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    // Parallel version for large groups of candidates
    private Participant findBestPlayerParallel(List<Participant> team, List<Participant> candidates) {
        try {
            int availableCores = Runtime.getRuntime().availableProcessors();
            int maxThreads = candidates.size() / MIN_CHUNK_SIZE;
            int numThreads = Math.min(availableCores, maxThreads);

            if (numThreads <= 1) {
                return findBestPlayerSequential(team, candidates);
            }

            int chunkSize = (int) Math.ceil((double) candidates.size() / numThreads);
            List<Future<ParticipantScore>> futures = new ArrayList<>();

            // Split work into chunks
            for (int i = 0; i < numThreads; i++) {
                int start = i * chunkSize;
                int end = Math.min((i + 1) * chunkSize, candidates.size());
                List<Participant> chunk = new ArrayList<>(candidates.subList(start, end));

                futures.add(executor.submit(() -> findBestInChunk(team, chunk)));
            }

            // Collect results from all threads
            Participant best = null;
            double bestScore = -1;

            for (Future<ParticipantScore> future : futures) {
                ParticipantScore result = future.get(5, TimeUnit.SECONDS);
                if (result != null && result.score > bestScore) {
                    bestScore = result.score;
                    best = result.participant;
                }
            }

            return best;

        } catch (TimeoutException e) {
            Logger.error("Parallel timeout - falling back to sequential");
            return findBestPlayerSequential(team, candidates);
        } catch (Exception e) {
            Logger.error("Parallel error: " + e.getMessage());
            return findBestPlayerSequential(team, candidates);
        }
    }

    // Used by parallel threads to compute the best candidate inside a chunk
    private ParticipantScore findBestInChunk(List<Participant> team, List<Participant> chunk) {
        Participant best = null;
        double bestScore = -1;

        for (Participant candidate : chunk) {
            if (breaksRules(team, candidate)) continue;

            double score = calculateScore(team, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return new ParticipantScore(best, bestScore);
    }

    // Checks if adding this candidate would break any team constraint
    private boolean breaksRules(List<Participant> team, Participant candidate) {

        // Check same-game limit (max 2)
        int sameGame = 0;
        for (Participant member : team) {
            if (member.getPreferredGame().equals(candidate.getPreferredGame())) sameGame++;
        }
        if (sameGame >= MAX_SAME_GAME) return true;

        // Check personality type distribution
        int leaders = 0, thinkers = 0, socializers = 0;
        for (Participant member : team) {
            if (member.getPersonalityType() == PersonalityType.LEADER) leaders++;
            if (member.getPersonalityType() == PersonalityType.THINKER) thinkers++;
            if (member.getPersonalityType() == PersonalityType.SOCIALIZER) socializers++;
        }

        if (candidate.getPersonalityType() == PersonalityType.LEADER && leaders >= MAX_LEADERS) return true;
        if (candidate.getPersonalityType() == PersonalityType.THINKER && thinkers >= MAX_THINKERS) return true;
        return candidate.getPersonalityType() == PersonalityType.SOCIALIZER && socializers >= MAX_SOCIALIZERS;
    }

    // Scoring system to determine how well a candidate fits the current team
    private double calculateScore(List<Participant> team, Participant candidate) {
        double score = 0;

        // Bonus for introducing a new role
        boolean hasRole = false;
        for (Participant member : team) {
            if (member.getPreferredRole() == candidate.getPreferredRole()) {
                hasRole = true;
                break;
            }
        }
        if (!hasRole) score += 25;

        // Skill balancing — prefer players close to team's avg skill
        double totalSkill = 0;
        for (Participant member : team) totalSkill += member.getSkillLevel();
        double avgSkill = team.isEmpty() ? 5 : totalSkill / team.size();
        score += 15 - Math.abs(candidate.getSkillLevel() - avgSkill);

        // Thinkers bonus if team has less than 2
        int thinkers = 0;
        for (Participant member : team) {
            if (member.getPersonalityType() == PersonalityType.THINKER) thinkers++;
        }
        if (candidate.getPersonalityType() == PersonalityType.THINKER && thinkers < 2) score += 10;

        return score;
    }

    // Basic rule: team must have at least N different role types
    private boolean hasEnoughRoles(List<Participant> team) {
        Set<RoleType> roles = new HashSet<>();
        for (Participant participant : team) {
            roles.add(participant.getPreferredRole());
        }
        return roles.size() >= MIN_DIFFERENT_ROLES;
    }

    // Builds overflow teams from leftovers without applying strict rules
    private void makeOverflowTeams(List<Participant> leftover) {
        Collections.shuffle(leftover);
        while (!leftover.isEmpty()) {
            Team team = new Team(nextTeamId++);
            int take = Math.min(targetTeamSize, leftover.size());
            for (int i = 0; i < take; i++) {
                team.addMember(leftover.removeFirst());
            }
            overflowTeams.add(team);
        }
    }

    // Attempts to swap players between strongest/weakest teams to smooth skill differences
    private void balanceSkills(List<Team> teams) {
        if (teams.size() < 2) return;

        // Up to 50 improvement iterations
        for (int i = 0; i < 50; i++) {
            teams.sort(Comparator.comparingDouble(Team::CalculateAvgSkill));
            Team weakestTeam = teams.getFirst();
            Team strongestTeam = teams.getLast();

            // Stop when skill difference becomes small
            if (strongestTeam.CalculateAvgSkill() - weakestTeam.CalculateAvgSkill() < 1.2) break;

            if (trySwapPlayers(strongestTeam, weakestTeam)) continue;
            break; // No viable swap found
        }
    }

    // Try swapping one member between teamA and teamB
    private boolean trySwapPlayers(Team teamA, Team teamB) {
        List<Participant> listA = new ArrayList<>(teamA.getParticipantList());
        List<Participant> listB = new ArrayList<>(teamB.getParticipantList());

        for (Participant playerA : listA) {
            for (Participant playerB : listB) {
                // Perform swap
                teamA.removeMember(playerA);
                teamA.addMember(playerB);
                teamB.removeMember(playerB);
                teamB.addMember(playerA);

                // Check rule compliance after swap
                if (!hasRuleProblem(teamA.getParticipantList()) && !hasRuleProblem(teamB.getParticipantList())) {
                    return true; // Swap accepted
                }

                // Undo invalid swap
                teamA.removeMember(playerB);
                teamA.addMember(playerA);
                teamB.removeMember(playerA);
                teamB.addMember(playerB);
            }
        }
        return false;
    }

    // Checks rule violations inside a team
    private boolean hasRuleProblem(List<Participant> team) {

        // Same game rule
        Map<String, Integer> games = new HashMap<>();
        for (Participant participant : team) {
            String game = participant.getPreferredGame();
            games.put(game, games.getOrDefault(game, 0) + 1);
            if (games.get(game) > MAX_SAME_GAME) return true;
        }

        // Personality rules
        int leaders = 0, thinkers = 0, socializers = 0;
        for (Participant participant : team) {
            if (participant.getPersonalityType() == PersonalityType.LEADER) leaders++;
            if (participant.getPersonalityType() == PersonalityType.THINKER) thinkers++;
            if (participant.getPersonalityType() == PersonalityType.SOCIALIZER) socializers++;
        }
        if (leaders > MAX_LEADERS || thinkers > MAX_THINKERS || socializers > MAX_SOCIALIZERS) return true;

        return false;
    }

    // Returns all teams in one list (balanced + overflow)
    public List<Team> getAllTeams() {
        List<Team> allTeams = new ArrayList<>();
        allTeams.addAll(balancedTeams);
        allTeams.addAll(overflowTeams);
        return allTeams;
    }

    // Helper to print all teams to console
    public void printAllTeams() {
        if (balancedTeams.isEmpty() && overflowTeams.isEmpty()) {
            System.out.println("No teams yet.");
            return;
        }

        System.out.println("\n" + "=".repeat(50));
        System.out.println("           FORMED TEAMS");
        System.out.println("=".repeat(50) + "\n");

        for (Team team : balancedTeams) printTeam(team, false);
        if (!overflowTeams.isEmpty()) {
            System.out.println("--- Overflow Teams (may break rules) ---\n");
            for (Team team : overflowTeams) printTeam(team, true);
        }
        printSummary();
    }

    // Print details of one team
    private void printTeam(Team team, boolean overflow) {
        System.out.println("=".repeat(50));
        System.out.println(" Team " + team.getTeam_id() + (overflow ? " [Overflow]" : " [Good]"));
        System.out.println("=".repeat(50));

        for (Participant participant : team.getParticipantList()) {
            System.out.printf(" %-6s | %-15s | %-12s | Skill: %2d | %-11s | %-10s%n",
                    participant.getId(),
                    participant.getName().length() > 15 ? participant.getName().substring(0,13)+".." : participant.getName(),
                    participant.getPreferredGame(),
                    participant.getSkillLevel(),
                    participant.getPreferredRole(),
                    participant.getPersonalityType());
        }
        System.out.printf("%n Avg Skill: %.2f | Size: %d/%d%n",
                team.CalculateAvgSkill(), team.getParticipantList().size(), targetTeamSize);

        if (overflow) {
            printViolation(team);
        }
        System.out.println();
    }

    // Shows rule violations for overflow teams
    private void printViolation(Team team) {
        List<String> violations = new ArrayList<>();

        List<Participant> members = team.getParticipantList();
        if (members.size() != targetTeamSize) violations.add("Wrong size (" + members.size() + "/" + targetTeamSize + ")");

        Map<String, Integer> games = new HashMap<>();
        for (Participant participant : members) {
            String game = participant.getPreferredGame();
            games.put(game, games.getOrDefault(game, 0) + 1);
        }
        for (String game : games.keySet()) {
            if (games.get(game) > MAX_SAME_GAME) violations.add("Too many " + game + " Players: " + games.get(game));
        }

        int leaders = 0, thinkers = 0, socializers = 0;
        for (Participant participant : members) {
            if (participant.getPersonalityType() == PersonalityType.LEADER) leaders++;
            if (participant.getPersonalityType() == PersonalityType.THINKER) thinkers++;
            if (participant.getPersonalityType() == PersonalityType.SOCIALIZER) socializers++;
        }
        if (leaders == 0) violations.add("No Leader");
        if (leaders > 1) violations.add("Too many Leaders:"+leaders);
        if (thinkers > 2) violations.add("Too many Thinkers:"+thinkers);
        if (socializers > 1) violations.add("Too many Socializers: "+socializers);

        Set<RoleType> roles = new HashSet<>();
        for (Participant participant : members) roles.add(participant.getPreferredRole());
        if (roles.size() < MIN_DIFFERENT_ROLES) violations.add("Only " + roles.size() + " roles");

        if (!violations.isEmpty()) {
            System.out.print(" Violation: ");
            for (int i = 0; i < violations.size(); i++) {
                System.out.print(violations.get(i));
                if (i < violations.size() - 1) System.out.print(" | ");
            }
            System.out.println();
        }
    }

    // Print summary of team distribution
    private void printSummary() {
        List<Team> allTeams = getAllTeams();
        int totalPeople = 0;
        for (Team team : allTeams) totalPeople += team.getParticipantList().size();

        System.out.println("=".repeat(50));
        System.out.println(" Summary");
        System.out.println(" Good Teams   : " + balancedTeams.size());
        System.out.println(" Overflow     : " + overflowTeams.size());
        System.out.println(" Total Teams  : " + allTeams.size());
        System.out.println(" Total Players: " + totalPeople);
        System.out.println("=".repeat(50) + "\n");
    }

    // Finds an existing suitable team for a newly added participant
    public Team findSuitableTeam(Participant participant) {
        for (Team team : balancedTeams) {
            if (team.getParticipantList().size() < targetTeamSize && !breaksRules(team.getParticipantList(), participant)) {
                return team;
            }
        }
        // If no suitable team, place in a new overflow team
        Team newTeam = new Team(nextTeamId++);
        overflowTeams.add(newTeam);
        return newTeam;
    }

    // Record for storing scoring results
    private record ParticipantScore(Participant participant, double score) {
    }
}
