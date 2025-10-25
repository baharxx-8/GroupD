package players.groupD;

import core.interfaces.IStateHeuristic;
import players.PlayerParameters;
import java.util.Arrays;

public class GroupDMCTSParams extends PlayerParameters {

    public double K = Math.sqrt(2);
    public int rolloutLength = 10;
    public int maxTreeDepth = 100;
    public double epsilon = 1e-6;
    public IStateHeuristic heuristic = null;

    public GroupDMCTSParams() {
        addTunableParameter("K", Math.sqrt(2), Arrays.asList(0.5, 1.0, Math.sqrt(2), 3.0));
        addTunableParameter("rolloutLength", 10, Arrays.asList(3, 10, 30, 100));
        addTunableParameter("maxTreeDepth", 100, Arrays.asList(10, 30, 100));
        addTunableParameter("epsilon", 1e-6);
    }

    @Override
    public void _reset() {
        super._reset();
        K = (double) getParameterValue("K");
        rolloutLength = (int) getParameterValue("rolloutLength");
        maxTreeDepth = (int) getParameterValue("maxTreeDepth");
        epsilon = (double) getParameterValue("epsilon");
    }

    @Override
    protected GroupDMCTSParams _copy() { return new GroupDMCTSParams(); }

    @Override
    public GroupDMCTSPlayer instantiate() {
        return new GroupDMCTSPlayer((GroupDMCTSParams) this.copy());
    }
}
