package utils;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import java.lang.management.ManagementFactory;
import java.util.TimerTask;

import static utils.TestUtil.OP_AVAILABLE_MEMORY;
import static utils.TestUtil.OP_CPU_LOAD;
import static utils.TestUtil.OP_RAM_USAGE;
import static utils.TestUtil.OP_READS;
import static utils.TestUtil.OP_WRITES;
import static utils.TestUtil.round;
import static utils.TestUtil.writeToFile;

public class MonitoringTask extends TimerTask {
    @Override
    public void run() {
        double cpuLoad = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        double cpuLoadRounded = round(cpuLoad, 2);
        writeToFile(String.valueOf(Double.valueOf(cpuLoadRounded).longValue()), OP_CPU_LOAD);
        monitor();
    }

    private void monitor() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        long availableMemory = hal.getMemory().getAvailable();
        long ramUsageInMB = (hal.getMemory().getTotal() - availableMemory) / 1024L;

        writeToFile(String.valueOf(ramUsageInMB), OP_RAM_USAGE);
        writeToFile(String.valueOf(availableMemory), OP_AVAILABLE_MEMORY);

        hal.getDiskStores().forEach( (disk) -> {
//            System.out.println("Disk: " + disk.getName() + " Model:" + disk.getModel() + " Reads: " + disk.getReads() + " Read Bytes: " + disk.getReadBytes() + " Writes: " + disk.getWrites() + " Write Bytes: " + disk.getWriteBytes());
            if ("disk3".equalsIgnoreCase(disk.getName())) {
                writeToFile(String.valueOf(disk.getReads()), OP_READS);
                writeToFile(String.valueOf(disk.getWrites()), OP_WRITES);
            }
        });
    }
}
