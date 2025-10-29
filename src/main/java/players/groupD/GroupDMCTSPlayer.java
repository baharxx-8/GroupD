package players.groupD;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;

import java.util.List;
import java.util.Random;

/**
 * Group D's implementation of an MCTS-based agent for the Sushi Go! game.
 * This version uses customizable parameters (GroupDMCTSParams)
 * and supports heuristic-guided rollouts.
 */
public class GroupDMCTSPlayer extends AbstractPlayer {

    // Random generator shared with parameters for consistent seeding
    protected Random rnd;

    // ========================= CONSTRUCTORS =========================

    /**
     * Default constructor with system time as seed.
     */
    public GroupDMCTSPlayer() {
        this(System.currentTimeMillis());
    }

    /**
     * Constructor using a specific random seed.
     * Initializes the player with default parameters.
     */
    public GroupDMCTSPlayer(long seed) {
        super(new GroupDMCTSParams(), "GroupD MCTS");
        parameters.setRandomSeed(seed);
        rnd = new Random(getParameters().getRandomSeed());
    }

    public GroupDMCTSPlayer(GroupDMCTSParams params) {
        super(params, "GroupD MCTS");
        rnd = new Random(params.getRandomSeed());
    }


    // ========================= MAIN ACTION SELECTION =========================

    /**
     * Called by the framework to obtain the player's next action.
     * Runs a full MCTS search before returning the best move.
     */
    @Override
    public AbstractAction _getAction(AbstractGameState gameState, List<AbstractAction> actions) {
        // ✅ Create the root node for the MCTS tree
        GroupDTreeNode root = new GroupDTreeNode(this, null, gameState, rnd);

        // ✅ Run the MCTS search loop
        root.mctsSearch();

        // ✅ Return the best action found by the search
        return root.bestAction();
    }

    // ========================= PARAMETER ACCESS =========================

    @Override
    public GroupDMCTSParams getParameters() {
        return (GroupDMCTSParams) parameters;
    }

    /**
     * Allows setting a custom heuristic dynamically (used by instantiate()).
     */
    public void setStateHeuristic(IStateHeuristic heuristic) {
        getParameters().heuristic = heuristic;
    }

    // ========================= UTILITY METHODS =========================

    /**
     * Deep copy of this player with a full parameter copy.
     */
    @Override
    public GroupDMCTSPlayer copy() {
        return new GroupDMCTSPlayer((GroupDMCTSParams) parameters.copy());
    }

    /**
     * String representation (useful for debugging and tournament logs).
     */
    @Override
    public String toString() {
        return "GroupD SushiGo MCTS Player";
    }
}
