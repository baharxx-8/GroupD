package players.groupD;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;
import java.util.List;
import java.util.Random;

public class GroupDMCTSPlayer extends AbstractPlayer {

    public GroupDMCTSPlayer() { this(System.currentTimeMillis()); }

    public GroupDMCTSPlayer(long seed) {
        super(new GroupDMCTSParams(), "Group D MCTS");
        parameters.setRandomSeed(seed);
        rnd = new Random(seed);

        GroupDMCTSParams p = getParameters();
        p.K = Math.sqrt(2);
        p.rolloutLength = 10;
        p.maxTreeDepth = 5;
        p.epsilon = 1e-6;
    }

    public GroupDMCTSPlayer(GroupDMCTSParams params) {
        super(params, "Group D MCTS");
        rnd = new Random(params.getRandomSeed());
    }

    @Override
    public AbstractAction _getAction(AbstractGameState gameState, List<AbstractAction> actions) {
        GroupDTreeNode root = new GroupDTreeNode(this, null, gameState, rnd);
        root.mctsSearch();
        return root.bestAction();
    }

    @Override
    public GroupDMCTSParams getParameters() { return (GroupDMCTSParams) parameters; }

    public void setStateHeuristic(IStateHeuristic heuristic) { getParameters().heuristic = heuristic; }

    @Override
    public GroupDMCTSPlayer copy() {
        return new GroupDMCTSPlayer((GroupDMCTSParams) parameters.copy());
    }

    @Override
    public String toString() { return "Group D MCTS Player"; }
}
