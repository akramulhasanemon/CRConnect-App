package com.example.crconnect

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.animation.PathInterpolatorCompat
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * ToastType defines notification categories for ToastHelper.
 */
enum class ToastType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * ToastHelper displays custom toast notifications attached to the Window via WindowManager.
 *
 * Features:
 * - Appears above all views, dialogs, and bottom sheets via WindowManager
 * - Accepts Activity or Application context
 * - Callable from any Activity, Fragment, or ViewModel
 * - Smooth spring slide-up entrance animation & shrink progress bar
 * - Light and Dark theme adaptive glassmorphic styling
 */
object ToastHelper {

    private const val TAG_DISMISSING = "CRCONNECT_TOAST_DISMISSING"
    private const val AUTO_DISMISS_MS = 3200L
    private const val MAX_TOASTS = 3
    private const val BOTTOM_OFFSET_DP = 28f
    private const val GAP_DP = 10f
    private const val MAX_WIDTH_DP = 336f

    private var currentActivityRef: WeakReference<Activity>? = null
    private var isInitialized = false
    private val containerMap = WeakHashMap<Activity, LinearLayout>()

    /**
     * Initializes Activity lifecycle tracking for ToastHelper.
     * Called automatically or explicitly from App.onCreate().
     */
    @JvmStatic
    fun init(application: Application) {
        if (isInitialized) return
        isInitialized = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivityRef?.get() === activity) {
                    currentActivityRef = null
                }
                removeToastContainer(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    /**
     * Primary static helper method:
     * ToastHelper.show(context, ToastType.SUCCESS, "Published to dashboard", "shaddhow is now visible to voters");
     */
    @JvmStatic
    @JvmOverloads
    fun show(
        context: Context?,
        toastType: ToastType,
        title: String?,
        body: String? = null
    ) {
        if (context == null) return
        ensureInitialized(context)

        val resolvedActivity = resolveActivity(context)
        val finalTitle = title ?: getDefaultTitle(toastType)
        val finalBody = body ?: ""

        if (resolvedActivity == null || resolvedActivity.isFinishing || resolvedActivity.isDestroyed) {
            showFallbackToast(context, finalTitle, finalBody)
            return
        }

        resolvedActivity.runOnUiThread {
            displayWindowManagerToast(resolvedActivity, toastType, finalTitle, finalBody)
        }
    }

    /**
     * Helper overload accepting title and body (defaulting to ToastType.INFO).
     */
    @JvmStatic
    fun show(context: Context?, title: String?, body: String?) {
        show(context, ToastType.INFO, title, body)
    }

    /**
     * Overload accepting single message and optional type.
     */
    @JvmStatic
    @JvmOverloads
    fun show(context: Context?, message: String?, toastType: ToastType? = null) {
        if (context == null || message.isNullOrBlank()) return
        val detectedType = toastType ?: detectType(message)
        show(context, detectedType, getDefaultTitle(detectedType), message)
    }

    @JvmStatic
    @JvmOverloads
    fun showSuccess(context: Context?, title: String?, body: String? = null) {
        show(context, ToastType.SUCCESS, title ?: "Success", body)
    }

    @JvmStatic
    @JvmOverloads
    fun showInfo(context: Context?, title: String?, body: String? = null) {
        show(context, ToastType.INFO, title ?: "Information", body)
    }

    @JvmStatic
    @JvmOverloads
    fun showWarning(context: Context?, title: String?, body: String? = null) {
        show(context, ToastType.WARNING, title ?: "Warning", body)
    }

    @JvmStatic
    @JvmOverloads
    fun showError(context: Context?, title: String?, body: String? = null) {
        show(context, ToastType.ERROR, title ?: "Error", body)
    }

    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            (context.applicationContext as? Application)?.let { init(it) }
        }
    }

    private fun resolveActivity(context: Context?): Activity? {
        if (context == null) return currentActivityRef?.get()
        var curr: Context? = context
        while (curr is ContextWrapper) {
            if (curr is Activity) return curr
            val base = curr.baseContext
            if (base === curr || base == null) break
            curr = base
        }
        return currentActivityRef?.get()
    }

    private fun displayWindowManagerToast(
        activity: Activity,
        toastType: ToastType,
        title: String,
        body: String
    ) {
        try {
            var container = containerMap[activity]

            if (container == null || container.parent == null) {
                container = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                    isClickable = false
                    isFocusable = false

                    layoutTransition = LayoutTransition().apply {
                        setDuration(LayoutTransition.CHANGE_APPEARING, 200)
                        setDuration(LayoutTransition.CHANGE_DISAPPEARING, 200)
                        setDuration(LayoutTransition.CHANGING, 200)
                    }
                }

                containerMap[activity] = container

                val wmParams = WindowManager.LayoutParams().apply {
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = dpToPx(activity, BOTTOM_OFFSET_DP)
                    format = PixelFormat.TRANSLUCENT
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    this.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                    token = activity.window?.decorView?.windowToken
                }

                val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (windowManager != null && wmParams.token != null) {
                    windowManager.addView(container, wmParams)
                } else if (activity.window != null) {
                    activity.window.decorView.post {
                        val token = activity.window?.decorView?.windowToken
                        if (token != null && windowManager != null && container.parent == null) {
                            wmParams.token = token
                            try {
                                windowManager.addView(container, wmParams)
                            } catch (_: Exception) {
                                attachToDecorView(activity, container)
                            }
                        } else if (container.parent == null) {
                            attachToDecorView(activity, container)
                        }
                    }
                } else {
                    showFallbackToast(activity, title, body)
                    return
                }
            }

            // Enforce max 3 stacked toasts
            while (container.childCount >= MAX_TOASTS) {
                val oldestView = container.getChildAt(0)
                container.removeView(oldestView)
            }

            val isDarkMode = isDarkMode(activity)
            val toastView = createToastView(activity, toastType, title, body, isDarkMode)

            val childParams = LinearLayout.LayoutParams(
                dpToPx(activity, MAX_WIDTH_DP).coerceAtMost((activity.resources.displayMetrics.widthPixels * 0.92f).toInt()),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }

            container.addView(toastView, childParams)
            updateStackMargins(container)

            // Entrance animation (spring slide up + scale + alpha)
            toastView.translationY = dpToPx(activity, 18f).toFloat()
            toastView.scaleX = 0.94f
            toastView.scaleY = 0.94f
            toastView.alpha = 0f

            val springInterpolator = PathInterpolatorCompat.create(0.34f, 1.4f, 0.64f, 1.0f)
            toastView.animate()
                .translationY(0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(340)
                .setInterpolator(springInterpolator)
                .start()

            // Auto dismiss & manual click dismiss
            val handler = Handler(Looper.getMainLooper())
            val dismissRunnable = Runnable { dismissToast(activity, container, toastView) }

            toastView.setOnClickListener {
                handler.removeCallbacks(dismissRunnable)
                dismissToast(activity, container, toastView)
            }

            val closeBtn = toastView.findViewById<View>(R.id.toastCloseBtn)
            closeBtn?.setOnClickListener {
                handler.removeCallbacks(dismissRunnable)
                dismissToast(activity, container, toastView)
            }

            handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)

        } catch (_: Exception) {
            showFallbackToast(activity, title, body)
        }
    }

    private fun attachToDecorView(activity: Activity, container: LinearLayout) {
        try {
            val root = (activity.findViewById<ViewGroup>(android.R.id.content) ?: activity.window?.decorView as? ViewGroup)
            if (root != null && container.parent == null) {
                val params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = dpToPx(activity, BOTTOM_OFFSET_DP)
                }
                root.addView(container, params)
            }
        } catch (_: Exception) {}
    }

    private fun isDarkMode(context: Context): Boolean {
        return try {
            val sessionManager = SessionManager(context)
            val currentAct = currentActivityRef?.get()
            val themeMode = when {
                context is TeacherDashboardActivity || currentAct is TeacherDashboardActivity -> sessionManager.teacherThemeMode
                context is StudentDashboardActivity || currentAct is StudentDashboardActivity -> sessionManager.studentThemeMode
                sessionManager.userRole.equals("Teacher", ignoreCase = true) -> sessionManager.teacherThemeMode
                sessionManager.userRole.equals("Student", ignoreCase = true) || sessionManager.userRole.equals("CR", ignoreCase = true) -> sessionManager.studentThemeMode
                else -> sessionManager.themeMode
            }
            val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            when (themeMode) {
                SessionManager.THEME_LIGHT -> false
                SessionManager.THEME_DARK -> true
                else -> systemDark
            }
        } catch (_: Exception) {
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private fun createToastView(
        context: Context,
        toastType: ToastType,
        title: String,
        body: String,
        isDarkMode: Boolean
    ): FrameLayout {
        val rootFrame = LayoutInflater.from(context).inflate(R.layout.toast_custom, null, false) as FrameLayout
        val style = getToastStyle(toastType, isDarkMode)

        // Set card background with proper theme styling & borders
        val cardBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            style.cardBgGradient
        ).apply {
            cornerRadius = dpToPx(context, 18f).toFloat()
            setStroke(dpToPx(context, 1f), style.cardStrokeColor)
        }
        rootFrame.background = cardBg
        rootFrame.elevation = dpToPx(context, if (isDarkMode) 12f else 8f).toFloat()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            rootFrame.outlineAmbientShadowColor = if (isDarkMode) "#8C000000".toColorInt() else "#1A000000".toColorInt()
            rootFrame.outlineSpotShadowColor = if (isDarkMode) "#8C000000".toColorInt() else "#20000000".toColorInt()
        }

        // Icon Container
        val iconFrame = rootFrame.findViewById<FrameLayout>(R.id.iconContainer)
        val iconBg = GradientDrawable(GradientDrawable.Orientation.TL_BR, style.iconBgGradient).apply {
            cornerRadius = dpToPx(context, 12f).toFloat()
            setStroke(dpToPx(context, 1f), style.iconStrokeColor)
        }
        iconFrame.background = iconBg

        val iconView = rootFrame.findViewById<ImageView>(R.id.toastIcon)
        iconView.setImageResource(style.iconRes)
        iconView.setColorFilter(style.iconTintColor)

        // Text
        val titleTextView = rootFrame.findViewById<TextView>(R.id.toastTitle)
        titleTextView.text = title
        titleTextView.setTextColor(style.titleColor)

        val bodyTextView = rootFrame.findViewById<TextView>(R.id.toastBody)
        if (body.isNotBlank()) {
            bodyTextView.text = body
            bodyTextView.visibility = View.VISIBLE
            bodyTextView.setTextColor(style.bodyColor)
        } else {
            bodyTextView.visibility = View.GONE
        }

        // Close Icon
        val closeButtonIcon = rootFrame.findViewById<ImageView>(R.id.toastCloseIcon)
        closeButtonIcon?.setColorFilter(style.closeIconColor)

        // Progress Bar Animation
        val progressBarView = rootFrame.findViewById<View>(R.id.toastProgressBar)
        progressBarView.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            style.barColors
        ).apply {
            cornerRadii = floatArrayOf(
                0f, 0f, 0f, 0f,
                dpToPx(context, 2f).toFloat(), dpToPx(context, 2f).toFloat(),
                dpToPx(context, 16f).toFloat(), dpToPx(context, 16f).toFloat()
            )
        }

        progressBarView.post {
            progressBarView.pivotX = 0f
            val barAnim = ObjectAnimator.ofFloat(progressBarView, "scaleX", 1f, 0f)
            barAnim.duration = AUTO_DISMISS_MS
            barAnim.interpolator = LinearInterpolator()
            barAnim.start()
        }

        return rootFrame
    }

    private fun updateStackMargins(container: LinearLayout) {
        val childCount = container.childCount
        for (i in 0 until childCount) {
            val child = container.getChildAt(i)
            val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
            params.bottomMargin = if (i == childCount - 1) 0 else dpToPx(container.context, GAP_DP)
            child.layoutParams = params
        }
    }

    private fun dismissToast(activity: Activity, container: LinearLayout, toastView: View) {
        if (toastView.tag == TAG_DISMISSING) return
        toastView.tag = TAG_DISMISSING

        toastView.animate()
            .translationY(dpToPx(toastView.context, 18f).toFloat())
            .scaleX(0.94f)
            .scaleY(0.94f)
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                try {
                    container.removeView(toastView)
                    updateStackMargins(container)
                    if (container.childCount == 0) {
                        removeToastContainer(activity)
                    }
                } catch (_: Exception) {}
            }
            .start()
    }

    private fun removeToastContainer(activity: Activity) {
        val container = containerMap.remove(activity) ?: return
        try {
            if (container.parent != null) {
                val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (container.isAttachedToWindow && windowManager != null) {
                    windowManager.removeViewImmediate(container)
                } else if (container.parent is ViewGroup) {
                    (container.parent as ViewGroup).removeView(container)
                }
            }
        } catch (_: Exception) {}
    }

    private class ToastStyle(
        val cardBgGradient: IntArray,
        val cardStrokeColor: Int,
        val iconBgGradient: IntArray,
        val iconStrokeColor: Int,
        val iconRes: Int,
        val titleColor: Int,
        val bodyColor: Int,
        val closeIconColor: Int,
        val barColors: IntArray,
        val iconTintColor: Int
    )

    private fun getToastStyle(toastType: ToastType, isDark: Boolean): ToastStyle {
        return if (isDark) {
            when (toastType) {
                ToastType.SUCCESS -> ToastStyle(
                    cardBgGradient = intArrayOf("#131B2E".toColorInt(), "#0F172A".toColorInt()),
                    cardStrokeColor = "#24354D".toColorInt(),
                    iconBgGradient = intArrayOf("#4022C55E".toColorInt(), "#2510B981".toColorInt()),
                    iconStrokeColor = "#4022C55E".toColorInt(),
                    iconRes = R.drawable.ic_toast_check,
                    titleColor = "#86EFAC".toColorInt(),
                    bodyColor = "#94A3B8".toColorInt(),
                    closeIconColor = "#64748B".toColorInt(),
                    barColors = intArrayOf("#22C55E".toColorInt(), "#10B981".toColorInt()),
                    iconTintColor = "#4ADE80".toColorInt()
                )
                ToastType.INFO -> ToastStyle(
                    cardBgGradient = intArrayOf("#131B2E".toColorInt(), "#0F172A".toColorInt()),
                    cardStrokeColor = "#24354D".toColorInt(),
                    iconBgGradient = intArrayOf("#403B82F6".toColorInt(), "#256366F1".toColorInt()),
                    iconStrokeColor = "#403B82F6".toColorInt(),
                    iconRes = R.drawable.ic_toast_vote,
                    titleColor = "#93C5FD".toColorInt(),
                    bodyColor = "#94A3B8".toColorInt(),
                    closeIconColor = "#64748B".toColorInt(),
                    barColors = intArrayOf("#3B82F6".toColorInt(), "#6366F1".toColorInt()),
                    iconTintColor = "#60A5FA".toColorInt()
                )
                ToastType.WARNING -> ToastStyle(
                    cardBgGradient = intArrayOf("#131B2E".toColorInt(), "#0F172A".toColorInt()),
                    cardStrokeColor = "#24354D".toColorInt(),
                    iconBgGradient = intArrayOf("#40F59E0B".toColorInt(), "#25EA580C".toColorInt()),
                    iconStrokeColor = "#40F59E0B".toColorInt(),
                    iconRes = R.drawable.ic_toast_clock,
                    titleColor = "#FCD34D".toColorInt(),
                    bodyColor = "#94A3B8".toColorInt(),
                    closeIconColor = "#64748B".toColorInt(),
                    barColors = intArrayOf("#F59E0B".toColorInt(), "#EA580C".toColorInt()),
                    iconTintColor = "#FCD34D".toColorInt()
                )
                ToastType.ERROR -> ToastStyle(
                    cardBgGradient = intArrayOf("#131B2E".toColorInt(), "#0F172A".toColorInt()),
                    cardStrokeColor = "#24354D".toColorInt(),
                    iconBgGradient = intArrayOf("#40EF4444".toColorInt(), "#25DC2626".toColorInt()),
                    iconStrokeColor = "#40EF4444".toColorInt(),
                    iconRes = R.drawable.ic_toast_shield,
                    titleColor = "#FCA5A5".toColorInt(),
                    bodyColor = "#94A3B8".toColorInt(),
                    closeIconColor = "#64748B".toColorInt(),
                    barColors = intArrayOf("#EF4444".toColorInt(), "#DC2626".toColorInt()),
                    iconTintColor = "#FCA5A5".toColorInt()
                )
            }
        } else {
            when (toastType) {
                ToastType.SUCCESS -> ToastStyle(
                    cardBgGradient = intArrayOf("#FFFFFFFF".toColorInt(), "#FFF8FAFC".toColorInt()),
                    cardStrokeColor = "#E2E8F0".toColorInt(),
                    iconBgGradient = intArrayOf("#DCFCE7".toColorInt(), "#BBF7D0".toColorInt()),
                    iconStrokeColor = "#86EFAC".toColorInt(),
                    iconRes = R.drawable.ic_toast_check,
                    titleColor = "#15803D".toColorInt(),
                    bodyColor = "#475569".toColorInt(),
                    closeIconColor = "#94A3B8".toColorInt(),
                    barColors = intArrayOf("#22C55E".toColorInt(), "#10B981".toColorInt()),
                    iconTintColor = "#16A34A".toColorInt()
                )
                ToastType.INFO -> ToastStyle(
                    cardBgGradient = intArrayOf("#FFFFFFFF".toColorInt(), "#FFF8FAFC".toColorInt()),
                    cardStrokeColor = "#E2E8F0".toColorInt(),
                    iconBgGradient = intArrayOf("#DBEAFE".toColorInt(), "#BFDBFE".toColorInt()),
                    iconStrokeColor = "#93C5FD".toColorInt(),
                    iconRes = R.drawable.ic_toast_vote,
                    titleColor = "#1D4ED8".toColorInt(),
                    bodyColor = "#475569".toColorInt(),
                    closeIconColor = "#94A3B8".toColorInt(),
                    barColors = intArrayOf("#3B82F6".toColorInt(), "#6366F1".toColorInt()),
                    iconTintColor = "#2563EB".toColorInt()
                )
                ToastType.WARNING -> ToastStyle(
                    cardBgGradient = intArrayOf("#FFFFFFFF".toColorInt(), "#FFF8FAFC".toColorInt()),
                    cardStrokeColor = "#E2E8F0".toColorInt(),
                    iconBgGradient = intArrayOf("#FEF3C7".toColorInt(), "#FDE68A".toColorInt()),
                    iconStrokeColor = "#FCD34D".toColorInt(),
                    iconRes = R.drawable.ic_toast_clock,
                    titleColor = "#B45309".toColorInt(),
                    bodyColor = "#475569".toColorInt(),
                    closeIconColor = "#94A3B8".toColorInt(),
                    barColors = intArrayOf("#F59E0B".toColorInt(), "#EA580C".toColorInt()),
                    iconTintColor = "#D97706".toColorInt()
                )
                ToastType.ERROR -> ToastStyle(
                    cardBgGradient = intArrayOf("#FFFFFFFF".toColorInt(), "#FFF8FAFC".toColorInt()),
                    cardStrokeColor = "#E2E8F0".toColorInt(),
                    iconBgGradient = intArrayOf("#FEE2E2".toColorInt(), "#FECACA".toColorInt()),
                    iconStrokeColor = "#FCA5A5".toColorInt(),
                    iconRes = R.drawable.ic_toast_shield,
                    titleColor = "#B91C1C".toColorInt(),
                    bodyColor = "#475569".toColorInt(),
                    closeIconColor = "#94A3B8".toColorInt(),
                    barColors = intArrayOf("#EF4444".toColorInt(), "#DC2626".toColorInt()),
                    iconTintColor = "#DC2626".toColorInt()
                )
            }
        }
    }

    private fun detectType(message: String): ToastType {
        val lower = message.lowercase()
        return when {
            lower.contains("fail") || lower.contains("error") || lower.contains("cannot") ||
                    lower.contains("invalid") || lower.contains("denied") || lower.contains("missing") ||
                    lower.contains("wrong") || lower.contains("problem") -> ToastType.ERROR

            lower.contains("success") || lower.contains("updated") || lower.contains("saved") ||
                    lower.contains("deleted") || lower.contains("reset") || lower.contains("assigned") ||
                    lower.contains("published") || lower.contains("resent") || lower.contains("added") ||
                    lower.contains("connected") -> ToastType.SUCCESS

            lower.contains("locked") || lower.contains("warning") || lower.contains("closed") || lower.contains("ended") ||
                    lower.contains("required") || lower.contains("no candidates") || lower.contains("not found") ||
                    lower.contains("please try again") || lower.contains("permission") -> ToastType.WARNING

            else -> ToastType.INFO
        }
    }

    private fun getDefaultTitle(toastType: ToastType): String {
        return when (toastType) {
            ToastType.SUCCESS -> "Success"
            ToastType.INFO -> "Information"
            ToastType.WARNING -> "Notice"
            ToastType.ERROR -> "Error"
        }
    }

    private fun showFallbackToast(context: Context, title: String, body: String) {
        val text = if (title.isNotBlank() && body.isNotBlank()) "$title: $body" else title.ifBlank { body }
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).roundToInt()
    }
}
