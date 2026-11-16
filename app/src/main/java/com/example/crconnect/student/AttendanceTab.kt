package com.example.crconnect.student

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@Composable
fun AttendanceTab(
    uiState: StudentPortalUiState,
    onRefreshHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val repo = RoutineRepository(context)
    val currentWeekRange = repo.getSundayWeekDateRange(Calendar.getInstance())

    // 1. Calculate live attendance for current Sunday-to-Saturday week
    val classMap = uiState.classMap
    val allClassesWithDay = classMap.flatMap { (dayIdx, classes) ->
        classes.map { dayIdx to it }
    }
    val evaluatedClasses = allClassesWithDay.filter { (dayIdx, item) ->
        val status = getClassTimingStatus(dayIdx, item.timeSlot)
        status == ClassTimingStatus.PAST || item.attendanceState != null
    }
    val attendedClasses = evaluatedClasses.filter { (dayIdx, item) ->
        getEffectiveAttendanceState(dayIdx, item) == "attended"
    }
    val missedClasses = evaluatedClasses.filter { (dayIdx, item) ->
        getEffectiveAttendanceState(dayIdx, item) == "missed"
    }
    val dynamicAttendancePct = if (evaluatedClasses.isNotEmpty()) {
        ((attendedClasses.size.toFloat() / evaluatedClasses.size) * 100).toInt()
    } else {
        0
    }

    val progress = if (evaluatedClasses.isNotEmpty()) dynamicAttendancePct / 100f else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
        label = "liveAttendanceProgress"
    )

    val screenBg = Color.Transparent

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Current Week Live Attendance Card ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = if (isDark) 0.dp else 4.dp,
                        shape = RoundedCornerShape(32.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    ),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header Row: Live badge & Date Range
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981).copy(alpha = if (isDark) 0.2f else 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = if (isDark) 0.4f else 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFF4ADE80) else Color(0xFF10B981))
                                )
                                Text(
                                    text = "CURRENT WEEK · LIVE",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF4ADE80) else Color(0xFF10B981),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Text(
                            text = currentWeekRange,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Main Metric Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "$dynamicAttendancePct%",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (evaluatedClasses.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurface
                                } else if (dynamicAttendancePct >= 80) {
                                    if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
                                } else if (dynamicAttendancePct >= 60) {
                                    if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                                } else {
                                    if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
                                }
                            )
                            Text(
                                text = if (evaluatedClasses.isNotEmpty()) {
                                    "Attended ${attendedClasses.size} of ${evaluatedClasses.size} classes"
                                } else {
                                    "0/0 classes evaluated (Starts at 0%)"
                                },
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (evaluatedClasses.isEmpty()) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else if (dynamicAttendancePct >= 80) {
                                Color(0xFF10B981).copy(alpha = if (isDark) 0.2f else 0.15f)
                            } else if (dynamicAttendancePct >= 60) {
                                Color(0xFF3B82F6).copy(alpha = if (isDark) 0.2f else 0.15f)
                            } else {
                                Color(0xFFEF4444).copy(alpha = if (isDark) 0.2f else 0.15f)
                            }
                        ) {
                            Text(
                                text = if (evaluatedClasses.isEmpty()) {
                                    "New Week"
                                } else if (dynamicAttendancePct >= 80) {
                                    "Excellent"
                                } else if (dynamicAttendancePct >= 60) {
                                    "Good"
                                } else {
                                    "Needs Attention"
                                },
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (evaluatedClasses.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else if (dynamicAttendancePct >= 80) {
                                    if (isDark) Color(0xFF4ADE80) else Color(0xFF10B981)
                                } else if (dynamicAttendancePct >= 60) {
                                    if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                                } else {
                                    if (isDark) Color(0xFFF87171) else Color(0xFFEF4444)
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (dynamicAttendancePct >= 80) {
                            if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E)
                        } else if (dynamicAttendancePct >= 60) {
                            if (isDark) Color(0xFF60A5FA) else Color(0xFF3B82F6)
                        } else {
                            if (isDark) Color(0xFFF87171) else Color(0xFFEF4444)
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // 3 Breakdown Stat Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatPill(
                            label = "Attended",
                            value = "${attendedClasses.size}",
                            color = if (isDark) Color(0xFF4ADE80) else Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "Missed",
                            value = "${missedClasses.size}",
                            color = if (isDark) Color(0xFFF87171) else Color(0xFFEF4444),
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "Evaluated",
                            value = "${evaluatedClasses.size}",
                            color = if (isDark) Color(0xFF60A5FA) else Color(0xFF0066FF),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Sunday Reset Notice
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF0066FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Weekly attendance runs Sunday to Saturday. Every Sunday, the previous week is archived to history and the new week resets to 0%.",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 2. History Section Header ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF0066FF),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Saved Weekly Attendance",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "${uiState.attendanceArchives.size} Archived",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // --- 3. Saved List of Previous Weeks ---
        if (uiState.attendanceArchives.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = if (isDark) 0.dp else 4.dp,
                            shape = RoundedCornerShape(24.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = (if (isDark) Color(0xFF3B82F6) else Color(0xFF0066FF)).copy(alpha = if (isDark) 0.2f else 0.1f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Archive",
                                    tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF0066FF),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Text(
                            text = "No Previous Weeks Archived Yet",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "When Sunday arrives, your completed week's attendance summary (date range, attended/total classes, percentage) will automatically be saved here.",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        } else {
            items(uiState.attendanceArchives, key = { it.weekKey }) { archive ->
                ArchiveWeekCard(archive = archive, isDark = isDark)
            }
        }

        // Bottom spacing for navigation bar
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArchiveWeekCard(
    archive: WeeklyAttendanceArchive,
    isDark: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 0.dp else 3.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Date range & Percentage chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = archive.dateRange.ifBlank { archive.weekKey },
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Week ID: ${archive.weekKey}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (archive.percentage >= 80) {
                        Color(0xFF10B981).copy(alpha = if (isDark) 0.2f else 0.15f)
                    } else if (archive.percentage >= 60) {
                        Color(0xFF3B82F6).copy(alpha = if (isDark) 0.2f else 0.15f)
                    } else {
                        Color(0xFFEF4444).copy(alpha = if (isDark) 0.2f else 0.15f)
                    }
                ) {
                    Text(
                        text = "${archive.percentage}%",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (archive.percentage >= 80) {
                            if (isDark) Color(0xFF4ADE80) else Color(0xFF10B981)
                        } else if (archive.percentage >= 60) {
                            if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                        } else {
                            if (isDark) Color(0xFFF87171) else Color(0xFFEF4444)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            // Progress Indicator
            LinearProgressIndicator(
                progress = { (archive.percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (archive.percentage >= 80) {
                    if (isDark) Color(0xFF4ADE80) else Color(0xFF22C55E)
                } else if (archive.percentage >= 60) {
                    if (isDark) Color(0xFF60A5FA) else Color(0xFF3B82F6)
                } else {
                    if (isDark) Color(0xFFF87171) else Color(0xFFEF4444)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Bottom details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${archive.attendedClasses} attended / ${archive.totalClasses} total classes",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF10B981).copy(alpha = if (isDark) 0.2f else 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Saved",
                            tint = if (isDark) Color(0xFF4ADE80) else Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Saved",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF4ADE80) else Color(0xFF10B981)
                        )
                    }
                }
            }
        }
    }
}
