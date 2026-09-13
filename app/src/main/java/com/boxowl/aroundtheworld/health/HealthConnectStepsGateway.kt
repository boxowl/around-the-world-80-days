package com.boxowl.aroundtheworld.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter

class HealthConnectStepsGateway(context: Context) : StepsGateway {
    private val appContext = context.applicationContext
    private val client get() = HealthConnectClient.getOrCreate(appContext)
    override fun availability() = when (HealthConnectClient.getSdkStatus(appContext)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.UPDATE_REQUIRED
        else -> Availability.UNAVAILABLE
    }
    override suspend fun hasPermission() = client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    override suspend fun aggregate(window: ReadWindow): StepTotal {
        val result = client.aggregate(AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
        ))
        return StepTotal(result[StepsRecord.COUNT_TOTAL], result.dataOrigins.map { it.packageName }.toSet())
    }
    companion object {
        val PERMISSIONS = setOf(HealthPermission.getReadPermission(StepsRecord::class))
    }
}
