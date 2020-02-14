import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;

public class TaskSimple extends CloudletSimple {

    public TaskSimple(long length, int pesNumber, UtilizationModel utilizationModel) {
        super(length, pesNumber, utilizationModel);
    }


}
