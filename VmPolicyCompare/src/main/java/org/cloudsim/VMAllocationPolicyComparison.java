package org.cloudsim;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;

import java.util.*;

public class VMAllocationPolicyComparison {
    public static void main(String[] args) {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            Datacenter datacenterSimple = createDatacenter("Datacenter_Simple", new VmAllocationPolicySimple(createHostList()));
            Datacenter datacenterRR = createDatacenter("Datacenter_RR", new VmAllocationPolicySimple(createHostList()));

            DatacenterBroker brokerSimple = new DatacenterBroker("Broker_Simple");
            DatacenterBroker brokerRR = new BrokerRoundRobin("Broker_RR");

            List<Vm> vmsSimple = createVMList(brokerSimple.getId(), 0);
            List<Vm> vmsRR = createVMList(brokerRR.getId(), 10);

            List<Cloudlet> cloudletsSimple = createCloudletList(brokerSimple.getId(), 0);
            List<Cloudlet> cloudletsRR = createCloudletList(brokerRR.getId(), 10);

            cloudletsSimple.sort((a, b) -> Long.compare(b.getCloudletLength(), a.getCloudletLength()));

            brokerSimple.submitVmList(vmsSimple);
            brokerSimple.submitCloudletList(cloudletsSimple);

            brokerRR.submitVmList(vmsRR);
            brokerRR.submitCloudletList(cloudletsRR);

            CloudSim.startSimulation();

            List<Cloudlet> resultsSimple = brokerSimple.getCloudletReceivedList();
            List<Cloudlet> resultsRR = brokerRR.getCloudletReceivedList();

            CloudSim.stopSimulation();

            System.out.println("=== VmAllocationPolicySimple Results ===");
            printResults(resultsSimple);
            printMakespan(resultsSimple, "VmAllocationPolicySimple");

            System.out.println("=== RoundRobin Cloudlet Assignment Results ===");
            printResults(resultsRR);
            printMakespan(resultsRR, "RoundRobin Cloudlet Assignment");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Datacenter createDatacenter(String name, VmAllocationPolicy policy) throws Exception {
        List<Host> hostList = createHostList();
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", hostList, 10.0,
                3.0, 0.05, 0.001, 0.0);
        return new Datacenter(name, characteristics, policy, new LinkedList<>(), 0);
    }

    private static List<Host> createHostList() {
        List<Host> hostList = new ArrayList<>();
        int[] mips = {4000, 1000, 500};
        for (int i = 0; i < 3; i++) {
            List<Pe> peList = List.of(new Pe(0, new PeProvisionerSimple(mips[i])));
            hostList.add(new Host(i,
                    new RamProvisionerSimple(2048),
                    new BwProvisionerSimple(10000),
                    1000000,
                    peList,
                    new VmSchedulerSpaceShared(peList)));
        }
        return hostList;
    }

    private static List<Vm> createVMList(int brokerId, int baseId) {
        List<Vm> vmList = new ArrayList<>();
        int[] mipsArray = {4000, 1000, 500};
        for (int i = 0; i < mipsArray.length; i++) {
            vmList.add(new Vm(baseId + i, brokerId, mipsArray[i], 1,
                    1024, 1000, 10000, "Xen", new CloudletSchedulerSpaceShared()));
        }
        return vmList;
    }

    private static List<Cloudlet> createCloudletList(int brokerId, int baseId) {
        List<Cloudlet> list = new ArrayList<>();
        UtilizationModel utilization = new UtilizationModelFull();
        long[] lengths = {20000, 80000, 10000, 120000, 160000, 5000, 8000, 30000, 40000, 90000, 150000, 60000};
        for (int i = 0; i < lengths.length; i++) {
            Cloudlet cl = new Cloudlet(baseId + i, lengths[i], 1, 300, 300,
                    utilization, utilization, utilization);
            cl.setUserId(brokerId);
            list.add(cl);
        }
        return list;
    }

    private static void printResults(List<Cloudlet> list) {
        for (Cloudlet cl : list) {
            if (cl.getCloudletStatus() == Cloudlet.SUCCESS) {
                System.out.printf("Cloudlet %d | VM: %d | Start: %.2f | Finish: %.2f | Time: %.2f\n",
                        cl.getCloudletId(), cl.getVmId(),
                        cl.getExecStartTime(), cl.getFinishTime(),
                        cl.getActualCPUTime());
            }
        }
    }

    private static void printMakespan(List<Cloudlet> list, String label) {
        double makespan = list.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);
        System.out.printf("Makespan for %s: %.2f seconds\n", label, makespan);
    }
}

class BrokerRoundRobin extends DatacenterBroker {
    private int lastVmIndex = -1;

    public BrokerRoundRobin(String name) throws Exception {
        super(name);
    }

    @Override
    protected void submitCloudlets() {
        int vmCount = getVmsCreatedList().size();
        for (Cloudlet cl : getCloudletList()) {
            lastVmIndex = (lastVmIndex + 1) % vmCount;
            cl.setVmId(getVmsCreatedList().get(lastVmIndex).getId());
        }
        super.submitCloudlets();
    }
}