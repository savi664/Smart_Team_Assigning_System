package Service;

import Model.*;

import java.util.*;
import java.util.concurrent.*;

public class TeamBuilder {

    private final List<Participant> allParticipants;
    private final List<Team> balancedTeams = new ArrayList<>();
    private final List<Team> overflowTeams = new ArrayList<>();
    private final int targetTeamSize;
    private int nextTeamId = 1;
    private final ExecutorService executor;

    // Rules (easy to read)
    private static final int MAX_SAME_GAME = 2;
    private static final int MAX_LEADERS = 1;
    private static final int MAX_THINKERS = 2;
    private static final int MAX_SOCIALIZERS = 1;
    private static final int MIN_DIFFERENT_ROLES = 3;

    // Parallelization thresholds - only use threads when it actually helps
    private static final int PARALLEL_THRESHOLD = 200;  // Changed from 50
    private static final int MIN_CHUNK_SIZE = 50;       // Minimum candidates per thread

    // Constructor for sequential mode (default)
    public TeamBuilder(List<Participant> participants, int teamSize) {
        this(participants, teamSize, null);
    }

    // Constructor for parallel mode
    public TeamBuilder(List<Participant> participants, int teamSize, ExecutorService executor) {
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("No participants given!");
        }
        if (teamSize < 2 || teamSize > 10) {
            throw new IllegalArgumentException("Team size must be 2–10");
        }
        this.allParticipants = new ArrayList<>(participants);
        this.targetTeamSize = teamSize;
        this.executor = executor;
    }

    public List<Team> formTeams() {
        balancedTeams.clear();
        overflowTeams.clear();
        nextTeamId = 1;

        List<Participant> remaining = new ArrayList<>(allParticipants);
        putLeadersFirst(remaining);
        Collections.shuffle(remaining); // shuffle

        while (remaining.size() >= targetTeamSize) {
            Team team = tryMakeCompliantTeam(remaining);
            if (team != null && team.getParticipantList().size() == targetTeamSize) {
                balancedTeams.add(team);
                remaining.removeAll(team.getParticipantList());
            } else {
                break;
            }
        }

        if (!remaining.isEmpty()) {
            makeOverflowTeams(remaining);
        }

        balanceSkills(balancedTeams);

        return getAllTeams();
    }

    private void putLeadersFirst(List<Participant> list) {
        list.sort((a, b) -> {
            if (a.getPersonalityType() == PersonalityType.LEADER) return -1;
            if (b.getPersonalityType() == PersonalityType.LEADER) return 1;
            return 0;
        });
    }

    private Team tryMakeCompliantTeam(List<Participant> pool) {
        List<Participant> available = new ArrayList<>(pool);
        Team team = new Team(nextTeamId++);
        List<Participant> chosen = new ArrayList<>();

        // Must have one leader
        Participant leader = null;
        for (Participant p : available) {
            if (p.getPersonalityType() == PersonalityType.LEADER) {
                leader = p;
                break;
            }
        }
        if (leader == null) return null;

        chosen.add(leader);
        available.remove(leader);

        // Add best players one by one
        while (chosen.size() < targetTeamSize && !available.isEmpty()) {
            Participant best = findBestPlayer(chosen, available);
            if (best == null) break;
            chosen.add(best);
            available.remove(best);
        }

        // Check if team is full and has enough different roles
        if (chosen.size() == targetTeamSize && hasEnoughRoles(chosen)) {
            for (Participant p : chosen) {
                team.addMember(p);
            }
            return team;
        }
        return null;
    }

    /**
     * Decides whether to use sequential or parallel processing
     * Only uses parallel when we have enough candidates to make it worthwhile
     */
    private Participant findBestPlayer(List<Participant> team, List<Participant> candidates) {
        // Only use parallel if executor exists AND we have enough candidates
        if (executor != null && candidates.size() >= PARALLEL_THRESHOLD) {
            return findBestPlayerParallel(team, candidates);
        }
        return findBestPlayerSequential(team, candidates);
    }

    /**
     * Sequential version - original implementation
     */
    private Participant findBestPlayerSequential(List<Participant> team, List<Participant> candidates) {
        Participant best = null;
        double bestScore = -1;

        for (Participant p : candidates) {
            if (breaksRules(team, p)) continue;

            double score = calculateScore(team, p);
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    /**
     * Parallel version - evaluates candidates in chunks across multiple threads
     * Split work smartly: don't create more threads than we need
     */
    private Participant findBestPlayerParallel(List<Participant> team, List<Participant> candidates) {
        try {
            // Figure out how many threads we actually need
            int availableCores = Runtime.getRuntime().availableProcessors();
            int maxThreads = candidates.size() / MIN_CHUNK_SIZE;  // Don't create tiny chunks
            int numThreads = Math.min(availableCores, maxThreads);

            // If we can't split meaningfully, just go sequential
            if (numThreads <= 1) {
                return findBestPlayerSequential(team, candidates);
            }

            int chunkSize = (int) Math.ceil((double) candidates.size() / numThreads);
            List<Future<ParticipantScore>> futures = new ArrayList<>();

            // Submit parallel tasks to evaluate chunks
            for (int i = 0; i < numThreads; i++) {
                int start = i * chunkSize;
                int end = Math.min((i + 1) * chunkSize, candidates.size());
                List<Participant> chunk = new ArrayList<>(candidates.subList(start, end));

                futures.add(executor.submit(() -> findBestInChunk(team, chunk)));
            }

            // Collect results and find overall best
            Participant best = null;
            double bestScore = -1;

            for (Future<ParticipantScore> future : futures) {
                ParticipantScore result = future.get(2, TimeUnit.SECONDS);  // Increased timeout
                if (result != null && result.score > bestScore) {
                    bestScore = result.score;
                    best = result.participant;
                }
            }

            return best;

        } catch (TimeoutException e) {
            // If it takes too long, just fall back to sequential
            System.err.println("Parallel processing timed out, using sequential instead");
            return findBestPlayerSequential(team, candidates);
        } catch (Exception e) {
            // If parallel fails for any reason, fall back to sequential
            return findBestPlayerSequential(team, candidates);
        }
    }

    /**
     * Finds best participant in a chunk of candidates (for parallel processing)
     */
    private ParticipantScore findBestInChunk(List<Participant> team, List<Participant> chunk) {
        Participant best = null;
        double bestScore = -1;

        for (Participant p : chunk) {
            if (breaksRules(team, p)) continue;

            double score = calculateScore(team, p);
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }

        return new ParticipantScore(best, bestScore);
    }

    private boolean breaksRules(List<Participant> team, Participant p) {
        // Same game check
        int sameGame = 0;
        for (Participant m : team) {
            if (m.getPreferredGame().equals(p.getPreferredGame())) sameGame++;
        }
        if (sameGame >= MAX_SAME_GAME) return true;

        // Personality check
        int leaders = 0, thinkers = 0, social = 0;
        for (Participant m : team) {
            if (m.getPersonalityType() == PersonalityType.LEADER) leaders++;
            if (m.getPersonalityType() == PersonalityType.THINKER) thinkers++;
            if (m.getPersonalityType() == PersonalityType.SOCIALIZER) social++;
        }
        if (p.getPersonalityType() == PersonalityType.LEADER && leaders >= MAX_LEADERS) return true;
        if (p.getPersonalityType() == PersonalityType.THINKER && thinkers >= MAX_THINKERS) return true;
        if (p.getPersonalityType() == PersonalityType.SOCIALIZER && social >= MAX_SOCIALIZERS) return true;

        return false;
    }

    private double calculateScore(List<Participant> team, Participant p) {
        double score = 0;

        // Bonus for new role
        boolean hasRole = false;
        for (Participant m : team) {
            if (m.getPreferredRole() == p.getPreferredRole()) {
                hasRole = true;
                break;
            }
        }
        if (!hasRole) score += 25;

        // Skill balance
        double totalSkill = 0;
        for (Participant m : team) totalSkill += m.getSkillLevel();
        double avg = team.isEmpty() ? 5 : totalSkill / team.size();
        score += 15 - Math.abs(p.getSkillLevel() - avg);

        // Like thinkers
        int thinkers = 0;
        for (Participant m : team) {
            if (m.getPersonalityType() == PersonalityType.THINKER) thinkers++;
        }
        if (p.getPersonalityType() == PersonalityType.THINKER && thinkers < 2) score += 10;

        return score;
    }

    private boolean hasEnoughRoles(List<Participant> team) {
        Set<RoleType> roles = new HashSet<>();
        for (Participant p : team) {
            roles.add(p.getPreferredRole());
        }
        return roles.size() >= MIN_DIFFERENT_ROLES;
    }

    private void makeOverflowTeams(List<Participant> left) {
        Collections.shuffle(left);
        while (!left.isEmpty()) {
            Team t = new Team(nextTeamId++);
            int take = Math.min(targetTeamSize, left.size());
            for (int i = 0; i < take; i++) {
                t.addMember(left.remove(0));
            }
            overflowTeams.add(t);
        }
    }

    private void balanceSkills(List<Team> teams) {
        if (teams.size() < 2) return;

        for (int i = 0; i < 50; i++) {
            teams.sort(Comparator.comparingDouble(Team::CalculateAvgSkill));
            Team weak = teams.getFirst();
            Team strong = teams.getLast();

            if (strong.CalculateAvgSkill() - weak.CalculateAvgSkill() < 1.2) break;

            if (trySwapPlayers(strong, weak)) continue;
            break;
        }
    }

    private boolean trySwapPlayers(Team a, Team b) {
        List<Participant> listA = new ArrayList<>(a.getParticipantList());
        List<Participant> listB = new ArrayList<>(b.getParticipantList());

        for (Participant pa : listA) {
            for (Participant pb : listB) {
                // Try swap
                a.removeMember(pa); a.addMember(pb);
                b.removeMember(pb); b.addMember(pa);

                if (!hasRuleProblem(a.getParticipantList()) && !hasRuleProblem(b.getParticipantList())) {
                    return true; // Good swap, keep it
                }

                // Undo
                a.removeMember(pb); a.addMember(pa);
                b.removeMember(pa); b.addMember(pb);
            }
        }
        return false;
    }

    private boolean hasRuleProblem(List<Participant> team) {
        // Same game
        Map<String, Integer> games = new HashMap<>();
        for (Participant p : team) {
            String g = p.getPreferredGame();
            games.put(g, games.getOrDefault(g, 0) + 1);
            if (games.get(g) > MAX_SAME_GAME) return true;
        }

        // Personality
        int leaders = 0, thinkers = 0, social = 0;
        for (Participant p : team) {
            if (p.getPersonalityType() == PersonalityType.LEADER) leaders++;
            if (p.getPersonalityType() == PersonalityType.THINKER) thinkers++;
            if (p.getPersonalityType() == PersonalityType.SOCIALIZER) social++;
        }
        if (leaders > MAX_LEADERS || thinkers > MAX_THINKERS || social > MAX_SOCIALIZERS) return true;

        return false;
    }

    public List<Team> getAllTeams() {
        List<Team> all = new ArrayList<>();
        all.addAll(balancedTeams);
        all.addAll(overflowTeams);
        return all;
    }

    public void printAllTeams() {
        if (balancedTeams.isEmpty() && overflowTeams.isEmpty()) {
            System.out.println("No teams yet.");
            return;
        }

        System.out.println("\n" + "=".repeat(50));
        System.out.println("           FORMED TEAMS");
        System.out.println("=".repeat(50) + "\n");

        for (Team t : balancedTeams) printTeam(t, false);
        if (!overflowTeams.isEmpty()) {
            System.out.println("--- Overflow Teams (may break rules) ---\n");
            for (Team t : overflowTeams) printTeam(t, true);
        }
        printSummary();
    }

    private void printTeam(Team team, boolean overflow) {
        System.out.println("=".repeat(50));
        System.out.println(" Team " + team.getTeam_id() + (overflow ? " [Overflow]" : " [Good]"));
        System.out.println("=".repeat(50));

        for (Participant p : team.getParticipantList()) {
            System.out.printf(" %-6s | %-15s | %-12s | Skill: %2d | %-11s | %-10s%n",
                    p.getId(),
                    p.getName().length() > 15 ? p.getName().substring(0,13)+".." : p.getName(),
                    p.getPreferredGame(),
                    p.getSkillLevel(),
                    p.getPreferredRole(),
                    p.getPersonalityType());
        }
        System.out.printf("%n Avg Skill: %.2f | Size: %d/%d%n",
                team.CalculateAvgSkill(), team.getParticipantList().size(), targetTeamSize);

        if (overflow) {
            printViolation(team);
        }
        System.out.println();
    }

    private void printViolation(Team team) {
        List<String> violation = new ArrayList<>();

        List<Participant> m = team.getParticipantList();
        if (m.size() != targetTeamSize) violation.add("Wrong size (" + m.size() + "/" + targetTeamSize + ")");

        Map<String, Integer> games = new HashMap<>();
        for (Participant p : m) {
            String g = p.getPreferredGame();
            games.put(g, games.getOrDefault(g, 0) + 1);
        }
        for (String g : games.keySet()) {
            if (games.get(g) > MAX_SAME_GAME) violation.add("Too many " + g + " Players" + games.get(g));
        }

        int leaders = 0, thinkers = 0, social = 0;
        for (Participant p : m) {
            if (p.getPersonalityType() == PersonalityType.LEADER) leaders++;
            if (p.getPersonalityType() == PersonalityType.THINKER) thinkers++;
            if (p.getPersonalityType() == PersonalityType.SOCIALIZER) social++;
        }
        if (leaders == 0) violation.add("No Leader");
        if (leaders > 1) violation.add("Too many Leaders:"+leaders);
        if (thinkers > 2) violation.add("Too many Thinkers:"+thinkers);
        if (social > 1) violation.add("Too many Socializers: "+social);

        Set<RoleType> roles = new HashSet<>();
        for (Participant p : m) roles.add(p.getPreferredRole());
        if (roles.size() < MIN_DIFFERENT_ROLES) violation.add("Only " + roles.size() + " roles");

        if (!violation.isEmpty()) {
            System.out.print(" Violation: ");
            for (int i = 0; i < violation.size(); i++) {
                System.out.print(violation.get(i));
                if (i < violation.size() - 1) System.out.print(" | ");
            }
            System.out.println();
        }
    }

    private void printSummary() {
        List<Team> all = getAllTeams();
        int totalPeople = 0;
        for (Team t : all) totalPeople += t.getParticipantList().size();

        System.out.println("=".repeat(50));
        System.out.println(" Summary");
        System.out.println(" Good Teams   : " + balancedTeams.size());
        System.out.println(" Overflow     : " + overflowTeams.size());
        System.out.println(" Total Teams  : " + all.size());
        System.out.println(" Total Players: " + totalPeople);
        System.out.println("=".repeat(50) + "\n");
    }

    public Team findSuitableTeam(Participant p) {
        for (Team t : balancedTeams) {
            if (t.getParticipantList().size() < targetTeamSize && !breaksRules(t.getParticipantList(), p)) {
                return t;
            }
        }
        Team newTeam = new Team(nextTeamId++);
        overflowTeams.add(newTeam);
        return newTeam;
    }

    /**
         * Helper class to hold participant and score for parallel processing
         */
        private record ParticipantScore(Participant participant, double score) {
    }
}