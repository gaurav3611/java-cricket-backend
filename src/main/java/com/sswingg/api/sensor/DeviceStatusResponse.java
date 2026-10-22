package com.sswingg.api.sensor;

import java.util.List;

public record DeviceStatusResponse(List<DeviceStatus> devices, int count) {
}
