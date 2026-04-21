//
// Copyright 2024 andiisking
//
// Contains code adapted from:
// - Dcx400 (2024) - Original implementation
// - Obtained via Klixr and G7X1 sources
// - HoyoSlave CMD TWEAK
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.kyroos.app.engine

import android.content.Context
import android.widget.Toast
import com.kyroos.app.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Profile {

    suspend fun runInitialSetup(context: Context) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Running Initial Setup & Cleaning System...", Toast.LENGTH_LONG).show()
        }

        withContext(Dispatchers.IO) {
            val scriptFile = File(context.getExternalFilesDir(null), "kyroos_setup.sh")
            val setupScript = """#!/system/bin/sh
cmd looper_stats reset
cmd looper_stats disable
dumpsys media.metrics --clear
for a in --reset --disable --disable-detailed-tracking; do
    dumpsys binder_calls_stats ${'$'}a
done
for b in --clear --stop-testing; do
    dumpsys procstats ${'$'}b
done
for c in ab-logging-disable dwb-logging-disable dmd-logging-disable; do
    cmd display ${'$'}c
done
for f in ${'$'}(dumpsys window | grep "^  Proto:" | sed 's/^  Proto: //' | tr ' ' '\n') ${'$'}(dumpsys window | grep "^  Logcat:" | sed 's/^  Logcat: //' | tr ' ' '\n'); do
    wm logging disable "${'$'}f"
    wm logging disable-text "${'$'}f"
done
echo 1 > /sys/kernel/tracing/buffer_size_kb
echo 1 > /sys/kernel/tracing/saved_cmdlines_size
pm list packages | cut -d: -f2 | head -n 20 | while read -r pkg; do
    pm log-visibility --disable "${'$'}pkg"
    cmd usagestats clear-last-used-timestamps "${'$'}pkg"
done
cmd blob_store clear-all-blobs
cmd blob_store clear-all-sessions
cmd accessibility stop-trace
cmd activity clear-watch-heap all
cmd activity clear-debug-app
cmd activity clear-exit-info
cmd activity untrack-associations
cmd autofill set log_level off
wm logging disable
wm logging disable-text
wm logging stop
wm tracing level critical
wm tracing size 0
ime tracing stop
cmd input_method tracing stop
cmd wifi set-verbose-logging disabled
cmd otadexopt cleanup
pm art cleanup
atrace --async_stop
logcat -G 64k
logcat -c
killall logcat 2>/dev/null
if [ "${'$'}(getprop ro.product.manufacturer)" = "Xiaomi" ]; then
    cmd miui_step_counter_service logging-disable
    cmd migard trace-buffer-size 0
    cmd migard stop-trace true
    lowtask charge_logger
fi
am kill-all
rm -f "${scriptFile.absolutePath}"
            """.trimIndent()

            try {
                scriptFile.writeText(setupScript)
                ShellUtils.execShizuku("chmod 755 \"${scriptFile.absolutePath}\"")
                ShellUtils.execShizuku("sh \"${scriptFile.absolutePath}\"")
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    suspend fun applySystemProfile(context: Context, profile: String) {
        val prof = profile.lowercase()
        val scriptFile = File(context.getExternalFilesDir(null), "kyroos_profile.sh")
        
        withContext(Dispatchers.Main) {
            val emoji = when(prof) {
                "gaming" -> "🎮"
                "extreme" -> "🔥"
                "powersave" -> "🔋"
                else -> "⚖️"
            }
            Toast.makeText(context, "Applying Profile: ${prof.uppercase()} $emoji", Toast.LENGTH_SHORT).show()
        }

        withContext(Dispatchers.IO) {
            val script = StringBuilder()
            script.append("#!/system/bin/sh\n\n")
            
            // Function get_fps
            script.append("""
get_fps() {
    # Langsung dari SurfaceFlinger (yang sudah terbukti work)
    local fps=${'$'}(dumpsys SurfaceFlinger 2>/dev/null | grep -E "refresh-rate|fps" | head -1 | awk '{print ${'$'}3}' | grep -Eo '[0-9]+')
    
    # Default safe
    if [ -z "${'$'}fps" ] || [ "${'$'}fps" -lt 30 ] || [ "${'$'}fps" -gt 240 ]; then
        fps=60
    fi
    
    echo "${'$'}fps"
}
            """.trimIndent())
            
            script.append("\n\n")
            
            // activity_manager function
            script.append("""
activity_manager() {
    factor="${'$'}{1:-1.0}"
    profile_name="${'$'}{2:-custom}"
    
    raw_data=${'$'}(dumpsys activity settings | grep -E '^[[:space:]]*[a-z_]+=[0-9.]+' | sed 's/^[[:space:]]*//')
    
    if [ -z "${'$'}raw_data" ]; then
        raw_data="max_cached_processes=32,background_settle_time=60000,fgservice_min_shown_time=2000,fgservice_min_report_time=3000,fgservice_screen_on_before_time=1000,fgservice_screen_on_after_time=5000,content_provider_retain_time=20000,gc_timeout=5000,gc_min_interval=60000,full_pss_min_interval=1200000,full_pss_lowered_interval=300000,power_check_interval=300000,service_usage_interaction_time=1800000,usage_stats_interaction_interval=7200000,service_restart_duration=1000,service_reset_run_duration=60000,service_restart_duration_factor=4,service_min_restart_time_between=10000,service_max_inactivity=1800000,service_bg_start_timeout=15000,service_bg_activity_start_timeout=10000,service_crash_restart_duration=1800000,service_crash_max_retry=16,memory_info_throttle_time=300000,top_to_fgs_grace_duration=15000,fgs_start_foreground_timeout=10000"
    fi

    tmp_file="/data/local/tmp/kyroos_am_tmp"
    raw_file="/data/local/tmp/kyroos_raw.txt"
    rm -f "${'$'}tmp_file" "${'$'}raw_file"

    # Simpan raw data ke file dulu
    echo "${'$'}raw_data" | tr ',' '\n' > "${'$'}raw_file"

    # Baca dari file (tidak pakai pipe, jadi tidak subshell)
    while IFS='=' read -r key val; do
        [ -z "${'$'}key" ] || [ -z "${'$'}val" ] && continue
        
        case "${'$'}key" in
            max_phantom_processes|service_restart_duration_factor|service_crash_max_retry)
                newval="${'$'}val"
                ;;
            max_cached_processes)
                newval=${'$'}(awk -v v="${'$'}val" -v f="${'$'}factor" 'BEGIN { printf "%d", v * f }')
                ;;
            *)
                newval=${'$'}(awk -v v="${'$'}val" -v f="${'$'}factor" 'BEGIN { printf "%.0f", v * f }')
                ;;
        esac
        
        echo "${'$'}key=${'$'}newval" >> "${'$'}tmp_file"
    done < "${'$'}raw_file"

    if [ -f "${'$'}tmp_file" ]; then
        final_consts=${'$'}(tr '\n' ',' < "${'$'}tmp_file" | sed 's/,$//')
        cmd settings put global activity_manager_constants "kyroos_${'$'}{profile_name},${'$'}final_consts"
        rm -f "${'$'}tmp_file" "${'$'}raw_file"
        echo "ActivityManager constants updated with factor ${'$'}factor"
        
    else
        return 1
    fi
}
""".trimIndent())

            script.append("\n\n")

            script.append("""
cmd settings delete global app_standby_constants
cmd settings delete global battery_saver_constants
cmd settings delete global device_idle_constants
cmd settings delete global job_scheduler_constants
cmd power set-adaptive-power-saver-enabled true 2
settings put global low_power 0 2>/dev/null
setprop debug.egl.hw 0
setprop debug.sf.hw 0
setprop debug.sf.multithreaded_present 0
setprop debug.hwui.render_ahead 0
setprop debug.hwui.target_cpu_time_percent 35

# Gunakan function get_fps yang sudah didefinisikan
FPS=${'$'}(get_fps)

setprop debug.sf.cached_set_max_defer_render_attmpts ${'$'}(( (75 * 150 + (FPS/2)) / FPS ))
setprop debug.sf.hint_margin_us ${'$'}((420 * FPS / 50))
setprop debug.sf.set_idle_timer_ms 1200
setprop debug.egl.blobcache.multifile_limit ${'$'}((24 * 1024 * 1024))
setprop debug.sqlite.journalmode MEMORY
setprop debug.sqlite.syncmode NORMAL
settings delete global game_driver_all_apps 2>/dev/null
settings delete global updatable_driver_all_apps 2>/dev/null
            """.trimIndent())
            script.append("\n\n")

            when (prof) {
                "extreme" -> {
                    script.append("""
activity_manager 2.0 extreme

# Gunakan function get_fps yang sudah didefinisikan
FPS=${'$'}(get_fps)

cmd power set-adaptive-power-saver-enabled false 
setprop debug.egl.hw 1
setprop debug.sf.hw 1
setprop debug.sf.multithreaded_present 1
setprop debug.hwui.render_ahead 1
setprop debug.hwui.target_cpu_time_percent 67
setprop debug.sf.cached_set_max_defer_render_attmpts ${'$'}(( (55 * 5 + (FPS/2)) / FPS ))
setprop debug.sf.hint_margin_us ${'$'}((1180 * FPS / 50))
setprop debug.sf.set_idle_timer_ms 10000
setprop debug.egl.blobcache.multifile_limit ${'$'}((1024 * 1024 * FPS))
setprop debug.sqlite.journalmode TRUNCATE
setprop debug.sqlite.syncmode NORMAL
cmd settings put global app_standby_constants elapsed_threshold_absolute=259200000,elapsed_threshold_interactive=129600000,elapsed_threshold_stable=345600000,strong_usage_duration=21600000,notification_seen_duration=259200000,system_update_usage_duration=129600000,prediction_timeout=129600000,sync_adapter_duration=1800000,exempted_sync_scheduled_nd_duration=900000,exempted_sync_start_duration=1800000,system_interaction_duration=900000,initial_foreground_service_start_duration=900000,stable_charging_threshold=180000,role_holder_duration=129600000,trigger_quota_bump_on_notification_seen=true,enable_restricted_bucket=false,restricted_bucket_delay_ms=900000,restricted_bucket_hold_duration_ms=2700000,slot_duration=10800000,window_size=20,max_bucket=35,parole_interval=21600000,parole_duration=1800000,notification_duration=120000,system_interaction_logging_duration=900000
cmd settings put global device_idle_constants light_after_inactive_to=900000,light_pre_idle_to=900000,light_idle_to=900000,light_idle_factor=4.0,light_max_idle_to=2700000,light_idle_maintenance_min_budget=180000,light_idle_maintenance_max_budget=900000,min_light_maintenance_time=15000,min_deep_maintenance_time=90000,inactive_to=2700000,sensing_to=12000,locating_to=15000,location_accuracy=50.0,motion_inactive_to=180000,idle_after_inactive_to=0,idle_to=14400000,wait_for_unlock=true,quick_doze_delay_to=120000,force_idle_delay=31500,light_standby_to=900000,light_standby_factor=4.0,max_light_standby_to=2700000,light_standby_maintenance_min_budget=180000,light_standby_maintenance_max_budget=900000
cmd settings put global job_scheduler_constants min_ready_non_active_jobs_count=3,max_non_active_jobs_count=15,min_charging_count=3,min_battery_not_low_count=3,min_storage_not_low_count=3,min_connectivity_count=3,min_content_count=3,min_idle_count=3,min_ready_jobs_count=3,heavy_use_factor=0.7,moderate_use_factor=0.3,fg_job_count=8,bg_normal_job_count=14,bg_moderate_job_count=24,bg_low_job_count=40,bg_critical_job_count=80,max_standard_reschedule_count=12,max_work_reschedule_count=20,min_linear_backoff_time=3000,min_exp_backoff_time=2000,max_backoff_time=10800000,min_backoff_time=3000,max_job_count_per_uid=250,max_job_count_active=200,max_job_count_working=120,max_job_count_failing=20,max_job_count_deferred=100,max_job_count_total=500
cmd thermalservice override-status 4
cmd notification post -t "KyrooS" "extreme" "EXTREME 🔥" >/dev/null 2>&1
                    """.trimIndent())
                }
                "gaming" -> {
                    script.append("""
activity_manager 1.7 gaming

# Gunakan function get_fps yang sudah didefinisikan
FPS=${'$'}(get_fps)

cmd power set-adaptive-power-saver-enabled false 
setprop debug.egl.hw 1
setprop debug.sf.hw 1
setprop debug.sf.multithreaded_present 1
setprop debug.hwui.render_ahead 1
setprop debug.hwui.target_cpu_time_percent 67
setprop debug.sf.cached_set_max_defer_render_attmpts ${'$'}(( (55 * 5 + (FPS/2)) / FPS ))
setprop debug.sf.hint_margin_us ${'$'}((1180 * FPS / 50))
setprop debug.sf.set_idle_timer_ms 10000
setprop debug.egl.blobcache.multifile_limit ${'$'}((1024 * 1024 * FPS))
setprop debug.sqlite.journalmode TRUNCATE
setprop debug.sqlite.syncmode NORMAL
cmd settings put global app_standby_constants elapsed_threshold_absolute=172800000,elapsed_threshold_interactive=86400000,elapsed_threshold_stable=259200000,strong_usage_duration=14400000,notification_seen_duration=172800000,system_update_usage_duration=86400000,prediction_timeout=86400000,sync_adapter_duration=1200000,exempted_sync_scheduled_nd_duration=600000,exempted_sync_start_duration=1200000,system_interaction_duration=600000,initial_foreground_service_start_duration=600000,stable_charging_threshold=120000,role_holder_duration=86400000,trigger_quota_bump_on_notification_seen=true,enable_restricted_bucket=false,restricted_bucket_delay_ms=600000,restricted_bucket_hold_duration_ms=1800000,slot_duration=7200000,window_size=15,max_bucket=40,parole_interval=43200000,parole_duration=1200000,notification_duration=60000,system_interaction_logging_duration=600000
cmd settings put global device_idle_constants light_after_inactive_to=600000,light_pre_idle_to=600000,light_idle_to=600000,light_idle_factor=3.0,light_max_idle_to=1800000,light_idle_maintenance_min_budget=120000,light_idle_maintenance_max_budget=600000,min_light_maintenance_time=10000,min_deep_maintenance_time=60000,inactive_to=1800000,sensing_to=8000,locating_to=10000,location_accuracy=30.0,motion_inactive_to=120000,idle_after_inactive_to=0,idle_to=7200000,wait_for_unlock=true,quick_doze_delay_to=60000,force_idle_delay=21000,light_standby_to=600000,light_standby_factor=3.0,max_light_standby_to=1800000,light_standby_maintenance_min_budget=120000,light_standby_maintenance_max_budget=600000
cmd settings put global job_scheduler_constants min_ready_non_active_jobs_count=2,max_non_active_jobs_count=10,min_charging_count=2,min_battery_not_low_count=2,min_storage_not_low_count=2,min_connectivity_count=2,min_content_count=2,min_idle_count=2,min_ready_jobs_count=2,heavy_use_factor=0.8,moderate_use_factor=0.4,fg_job_count=6,bg_normal_job_count=10,bg_moderate_job_count=18,bg_low_job_count=30,bg_critical_job_count=60,max_standard_reschedule_count=8,max_work_reschedule_count=15,min_linear_backoff_time=5000,min_exp_backoff_time=3000,max_backoff_time=14400000,min_backoff_time=5000,max_job_count_per_uid=200,max_job_count_active=150,max_job_count_working=80,max_job_count_failing=15,max_job_count_deferred=70,max_job_count_total=400
cmd thermalservice override-status 4
cmd notification post -t "KyrooS" "gaming" "GAMING 🎮" >/dev/null 2>&1
                    """.trimIndent())
                }
                "powersave" -> {
                    script.append("""
activity_manager 0.7 powersave
cmd power set-mode 1

# Gunakan function get_fps yang sudah didefinisikan
FPS=${'$'}(get_fps)

cmd settings put global app_standby_constants elapsed_threshold_absolute=43200000,elapsed_threshold_interactive=21600000,elapsed_threshold_stable=86400000,strong_usage_duration=3600000,notification_seen_duration=43200000,system_update_usage_duration=21600000,prediction_timeout=21600000,sync_adapter_duration=300000,exempted_sync_scheduled_nd_duration=150000,exempted_sync_start_duration=300000,system_interaction_duration=150000,initial_foreground_service_start_duration=150000,stable_charging_threshold=30000,role_holder_duration=21600000,trigger_quota_bump_on_notification_seen=true,enable_restricted_bucket=true,restricted_bucket_delay_ms=150000,restricted_bucket_hold_duration_ms=450000,slot_duration=1800000,window_size=7,max_bucket=50,parole_interval=129600000,parole_duration=300000,notification_duration=15000,system_interaction_logging_duration=150000
cmd settings put global battery_saver_constants adjust_brightness_factor=0.3,adjust_brightness_disabled=false,advertise_is_enabled=false,animation_disabled=true,datasaver_disabled=false,enable_night_mode=true,firewall_disabled=false,force_all_apps_standby=true,force_background_check=true,gps_mode=2,job_scheduler_disabled=false,quick_doze_enabled=true,sound_trigger_disabled=true,vibration_disabled=true,fullbackup_deferred=true,keyvaluebackup_deferred=true,gps_disabled=true,fullbackup_deferred_wait_time=120000,keyvaluebackup_deferred_wait_time=120000,location_mode=3,launch_boost_disabled=true,optional_sensors_disabled=true,aod_disabled=true,sound_trigger_mode=2,vibration_mode=2,adjust_brightness_mode=1
cmd settings put global device_idle_constants light_after_inactive_to=150000,light_pre_idle_to=150000,light_idle_to=150000,light_idle_factor=1.5,light_max_idle_to=450000,light_idle_maintenance_min_budget=30000,light_idle_maintenance_max_budget=150000,min_light_maintenance_time=3000,min_deep_maintenance_time=15000,inactive_to=450000,sensing_to=2000,locating_to=2500,location_accuracy=40.0,motion_inactive_to=30000,idle_after_inactive_to=0,idle_to=1800000,wait_for_unlock=true,quick_doze_delay_to=15000,force_idle_delay=5250,light_standby_to=150000,light_standby_factor=1.5,max_light_standby_to=450000,light_standby_maintenance_min_budget=30000,light_standby_maintenance_max_budget=150000
cmd settings put global job_scheduler_constants min_ready_non_active_jobs_count=0,max_non_active_jobs_count=3,min_charging_count=1,min_battery_not_low_count=0,min_storage_not_low_count=0,min_connectivity_count=1,min_content_count=0,min_idle_count=0,min_ready_jobs_count=0,heavy_use_factor=1.0,moderate_use_factor=0.7,fg_job_count=2,bg_normal_job_count=4,bg_moderate_job_count=8,bg_low_job_count=12,bg_critical_job_count=25,max_standard_reschedule_count=3,max_work_reschedule_count=6,min_linear_backoff_time=20000,min_exp_backoff_time=10000,max_backoff_time=36000000,min_backoff_time=20000,max_job_count_per_uid=100,max_job_count_active=60,max_job_count_working=30,max_job_count_failing=5,max_job_count_deferred=30,max_job_count_total=200
cmd thermalservice override-status 2
cmd notification post -t "KyrooS" "powersave" "POWERSAVE 🔋" >/dev/null 2>&1
                    """.trimIndent())
                }
                else -> {
                    script.append("""
activity_manager 1.0 balanced

# Gunakan function get_fps yang sudah didefinisikan
FPS=${'$'}(get_fps)

cmd settings put global app_standby_constants elapsed_threshold_absolute=86400000,elapsed_threshold_interactive=43200000,elapsed_threshold_stable=172800000,strong_usage_duration=7200000,notification_seen_duration=86400000,system_update_usage_duration=43200000,prediction_timeout=43200000,sync_adapter_duration=600000,exempted_sync_scheduled_nd_duration=300000,exempted_sync_start_duration=600000,system_interaction_duration=300000,initial_foreground_service_start_duration=300000,stable_charging_threshold=60000,role_holder_duration=43200000,trigger_quota_bump_on_notification_seen=true,enable_restricted_bucket=true,restricted_bucket_delay_ms=300000,restricted_bucket_hold_duration_ms=900000,slot_duration=3600000,window_size=10,max_bucket=45,parole_interval=86400000,parole_duration=600000,notification_duration=30000,system_interaction_logging_duration=300000
cmd settings put global device_idle_constants light_after_inactive_to=300000,light_pre_idle_to=300000,light_idle_to=300000,light_idle_factor=2.0,light_max_idle_to=900000,light_idle_maintenance_min_budget=60000,light_idle_maintenance_max_budget=300000,min_light_maintenance_time=5000,min_deep_maintenance_time=30000,inactive_to=900000,sensing_to=4000,locating_to=5000,location_accuracy=20.0,motion_inactive_to=60000,idle_after_inactive_to=0,idle_to=3600000,wait_for_unlock=true,quick_doze_delay_to=30000,force_idle_delay=10500,light_standby_to=300000,light_standby_factor=2.0,max_light_standby_to=900000,light_standby_maintenance_min_budget=60000,light_standby_maintenance_max_budget=300000
cmd settings put global job_scheduler_constants min_ready_non_active_jobs_count=1,max_non_active_jobs_count=5,min_charging_count=1,min_battery_not_low_count=1,min_storage_not_low_count=1,min_connectivity_count=1,min_content_count=1,min_idle_count=1,min_ready_jobs_count=1,heavy_use_factor=0.9,moderate_use_factor=0.5,fg_job_count=4,bg_normal_job_count=6,bg_moderate_job_count=12,bg_low_job_count=20,bg_critical_job_count=40,max_standard_reschedule_count=5,max_work_reschedule_count=10,min_linear_backoff_time=10000,min_exp_backoff_time=5000,max_backoff_time=18000000,min_backoff_time=10000,max_job_count_per_uid=150,max_job_count_active=100,max_job_count_working=50,max_job_count_failing=10,max_job_count_deferred=50,max_job_count_total=300
cmd thermalservice override-status 1
cmd notification post -t "KyrooS" "balanced" "BALANCED ⚖️" >/dev/null 2>&1
                    """.trimIndent())
                }
            }

            script.append("\nrm -f \"${scriptFile.absolutePath}\"\n")

            try {
                scriptFile.writeText(script.toString())
                ShellUtils.execShizuku("chmod 755 \"${scriptFile.absolutePath}\"")
                ShellUtils.execShizuku("sh \"${scriptFile.absolutePath}\"")
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
