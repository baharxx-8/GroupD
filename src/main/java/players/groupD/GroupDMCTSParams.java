package players.groupD;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import players.PlayerParameters;

import java.util.Arrays;
import java.util.Random;

public class GroupDMCTSParams extends PlayerParameters {

    // --- MCTS main parameters (tunable) ---
    public double K = Math.sqrt(2);         // Exploration constant for UCB
    public int rolloutLength = 10;          // Maximum depth for rollout simulations
    public int maxTreeDepth = 100;          // Maximum depth of the search tree
    public double epsilon = 1e-6;           // Small constant to avoid division by zero

    // --- Additional parameters for experimentation ---
    public double epsilonGreedy = 0.2;          // Probability of taking a random action in rollout
    public int timePerMoveMs = 1000;            // Time budget per decision (milliseconds)
    public int minIterations = 50;              // Minimum iterations before stopping
    public boolean useHeuristicRollout = true;  // Whether to use heuristic-guided rollouts
    public boolean useInterimHeuristicValue = true; // Whether to use heuristic value for leaf nodes

    // --- Heuristic reference (default can be replaced) ---
    public IStateHeuristic heuristic;

    private final Random rnd = new Random();

    // ========================= CONSTRUCTOR =========================
    public GroupDMCTSParams() {

        // Tunable parameters for automated tuning experiments
        addTunableParameter("K", Math.sqrt(2), Arrays.asList(0.5, 1.0, Math.sqrt(2), 3.0));
        addTunableParameter("rolloutLength", 10, Arrays.asList(5, 10, 15, 20));
        addTunableParameter("maxTreeDepth", 100, Arrays.asList(30, 50, 100));
        addTunableParameter("epsilon", 1e-6);

        // 🔥 Default heuristic (generic, not game-specific)
        heuristic = (state, playerID) -> {
            double myScore = state.getGameScore(playerID);

            // Compare to the current best opponent
            double maxOpponent = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < state.getNPlayers(); i++) {
                if (i != playerID) {
                    maxOpponent = Math.max(maxOpponent, state.getGameScore(i));
                }
            }

            if (Double.isInfinite(maxOpponent)) maxOpponent = 0;
            double scoreDiff = myScore - maxOpponent;

            // Estimate game progress (based on turn number)
            int turn = state.getTurnCounter();
            double progress = Math.min(1.0, turn / 100.0);

            // Adjust score weighting based on progress
            double weightMyScore = (progress < 0.5) ? 0.7 : 1.2;
            double dynamicScore = weightMyScore * myScore + 0.30 * scoreDiff;

            // Penalize overconfidence near the end
            if (progress > 0.8)
                dynamicScore -= 0.20 * Math.abs(scoreDiff);

            // Add small Gaussian noise to break ties
            return dynamicScore + rnd.nextGaussian() * 0.005;
        };
    }

    // ========================= RESET =========================
    @Override
    public void _reset() {
        super._reset();
        K = (double) getParameterValue("K");
        rolloutLength = (int) getParameterValue("rolloutLength");
        maxTreeDepth = (int) getParameterValue("maxTreeDepth");
        epsilon = (double) getParameterValue("epsilon");
    }

    // ========================= DEEP COPY =========================
    @Override
    protected GroupDMCTSParams _copy() {
        GroupDMCTSParams p = new GroupDMCTSParams();

        // Basic parameters
        p.K = K;
        p.rolloutLength = rolloutLength;
        p.maxTreeDepth = maxTreeDepth;
        p.epsilon = epsilon;

        // Additional parameters
        p.epsilonGreedy = epsilonGreedy;
        p.timePerMoveMs = timePerMoveMs;
        p.minIterations = minIterations;
        p.useHeuristicRollout = useHeuristicRollout;
        p.useInterimHeuristicValue = useInterimHeuristicValue;

        // Heuristic and randomness
        p.heuristic = heuristic;
        p.setRandomSeed(getRandomSeed());

        return p;
    }

    // ========================= INSTANTIATE PLAYER =========================
    @Override
    public GroupDMCTSPlayer instantiate() {
        GroupDMCTSPlayer player = new GroupDMCTSPlayer((GroupDMCTSParams) this.copy());

        try {
            // If the game is Sushi Go, load the specific heuristic
            Class<?> gsClass = Class.forName("games.sushigo.SGGameState");
            if (gsClass != null) {
                player.setStateHeuristic(new SushiGoHeuristic());
                System.out.println("🧠 SushiGoHeuristic successfully loaded!");
            }
        } catch (ClassNotFoundException e) {
            // Default heuristic for other games
            System.out.println("⚙️ Default heuristic will be used.");
        }

        return player;
    }

    // ========================= HEURISTIC GETTER =========================
    @Override
    public IStateHeuristic getStateHeuristic() {
        // Use custom heuristic if available, otherwise fall back to framework’s default
        return (heuristic != null) ? heuristic : AbstractGameState::getHeuristicScore;
    }
}
