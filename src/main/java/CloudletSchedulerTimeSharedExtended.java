import java.util.List;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletExecution;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerAbstract;

public class CloudletSchedulerTimeSharedExtended extends CloudletSchedulerAbstractExtended {
    public CloudletSchedulerTimeSharedExtended() {
    }

    public List<CloudletExecution> getCloudletWaitingList() {
        return super.getCloudletWaitingList();
    }

    private double movePausedCloudletToExecListAndGetExpectedFinishTime(CloudletExecution cloudlet) {
        this.getCloudletPausedList().remove(cloudlet);
        this.addCloudletToExecList(cloudlet);
        return this.cloudletEstimatedFinishTime(cloudlet, this.getVm().getSimulation().clock());
    }

    public double cloudletResume(Cloudlet cloudlet) {
        return (Double)this.findCloudletInList(cloudlet, this.getCloudletPausedList()).map(this::movePausedCloudletToExecListAndGetExpectedFinishTime).orElse(0.0D);
    }

    protected boolean canExecuteCloudletInternal(CloudletExecution cloudlet) {
        return true;
    }
}
