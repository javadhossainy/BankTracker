package com.example.banktracker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

class MainActivity : Activity() {

    companion object {
        var onSmsReceivedUIUpdate: (() -> Unit)? = null
        var onTestSmsReceived: ((type: String, amount: Long, dateStr: String) -> Unit)? = null

        const val PREFS_NAME = "BankTrackerPrefs"
        const val KEY_TRANSACTIONS = "transactions"
        const val KEY_BALANCE = "last_balance"
        const val KEY_BALANCE_DATE = "last_balance_date"
        const val KEY_CYCLE_START = "cycle_start_ms"

        // ۴ دسته‌بندی اصلی هزینه‌ها
        val EXPENSE_CATEGORIES = listOf(
            "خرج ضروری",
            "تفریح",
            "غیر ضروری",
            "غیر تفریحی"
        )

        // ۴ دسته‌بندی درآمدها برای نمودار دایره‌ای درآمد
        val INCOME_CATEGORIES = listOf(
            "حقوق و دستمزد",
            "کسب‌وکار و فروش",
            "انتقال وجه",
            "سایر واریزی‌ها"
        )

        // رنگ‌های نئونی درخشان مخصوص تم شیشه‌ای (الهام‌گرفته از پالت چرتکه)
        val CHART_COLORS_EXPENSE = listOf("#FF5E8E", "#22D3EE", "#3B82F6", "#FBBF24")
        val CHART_COLORS_INCOME = listOf("#34D399", "#38BDF8", "#818CF8", "#FACC15")
    }

    private lateinit var rootLayout: LinearLayout
    private lateinit var topSubTabBar: LinearLayout
    private lateinit var pageContentContainer: LinearLayout
    private lateinit var bottomNavBar: LinearLayout

    // 0 = نمودارها | 1 = تفکیک ۱۰ روزه | 2 = گردش حساب | 3 = تست پیامک
    private var currentTabIndex = 0
    private var showExpenseInBarChart = true
    private var isDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        window.statusBarColor = Color.parseColor("#090E1A")

        // پس‌زمینه گرادینت کهکشانی تیره برای جلوه دادن به کادرهای شیشه‌ای (Glassmorphism)
        val cosmicBgGradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#090E1A"),
                Color.parseColor("#1E1B4B"),
                Color.parseColor("#0F3443")
            )
        )

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cosmicBgGradient
        }

        // ۱. هدر شیشه‌ای بالایی (به سبک چرتکه)
        val topAppBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createGlassBg("#24FFFFFF", "#33FFFFFF", 0f, 1)
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        val appLogoBadge = TextView(this).apply {
            text = "٪"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = createGlassBg("#4D38BDF8", "#9938BDF8", dp(10).toFloat(), 2)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        }
        topAppBar.addView(appLogoBadge)

        val appTitle = TextView(this).apply {
            text = "مدیریت مالی و خرج ۱۰ روزه"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topAppBar.addView(appTitle)

        val quickTestBadge = TextView(this).apply {
            text = "بخش تست"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = createGlassBg("#2638BDF8", "#8038BDF8", dp(14).toFloat(), 2)
            setOnClickListener {
                currentTabIndex = 3
                refreshUI()
            }
        }
        topAppBar.addView(quickTestBadge)
        rootLayout.addView(topAppBar)

        // ۲. نوار تب‌های شیشه‌ای خط‌دار بالای صفحه (مشابه چرتکه)
        topSubTabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBg("#1AFFFFFF", "#26FFFFFF", 0f, 1)
        }
        rootLayout.addView(topSubTabBar)

        // ۳. محتوای قابل اسکرول صفحات با فاصله‌گذاری دلباز
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        pageContentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(30))
        }

        scrollView.addView(pageContentContainer)
        rootLayout.addView(scrollView)

        // ۴. نوار ناوبری شیشه‌ای پایین صفحه (۴ تب)
        bottomNavBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBg("#29FFFFFF", "#4DFFFFFF", dp(22).toFloat(), 2)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(12), 0, dp(12), dp(12))
            }
        }
        rootLayout.addView(bottomNavBar)

        setContentView(rootLayout)

        initCycleIfNeeded()
        requestNeededPermissions()
        // خواندن خودکار آخرین موجودی واقعی از پیامک‌های گوشی در بدو ورود
        syncLatestBalanceFromSmsInbox(showToast = false)
    }

    override fun onResume() {
        super.onResume()
        onSmsReceivedUIUpdate = {
            runOnUiThread {
                refreshUI()
                checkAndAskPendingTransactions()
            }
        }
        onTestSmsReceived = { type, amount, dateStr ->
            runOnUiThread {
                showTestTransactionDialog(type, amount, dateStr)
            }
        }
        syncLatestBalanceFromSmsInbox(showToast = false)
        refreshUI()
        checkAndAskPendingTransactions()
    }

    override fun onPause() {
        super.onPause()
        onSmsReceivedUIUpdate = null
        onTestSmsReceived = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            syncLatestBalanceFromSmsInbox(showToast = true)
            refreshUI()
        }
    }

    private fun initCycleIfNeeded() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_CYCLE_START)) {
            prefs.edit().putLong(KEY_CYCLE_START, System.currentTimeMillis()).apply()
        }
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val perms = mutableListOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val notGranted = perms.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (notGranted.isNotEmpty()) {
                requestPermissions(notGranted.toTypedArray(), 101)
            }
        }
    }

    // =========================================================================
    // خواندن خودکار آخرین موجودی از صندوق پیامک‌های گوشی (SMS Inbox)
    // =========================================================================
    private fun syncLatestBalanceFromSmsInbox(showToast: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val inboxUri = Uri.parse("content://sms/inbox")
            val cursor = contentResolver.query(
                inboxUri,
                arrayOf("_id", "body", "date"),
                null,
                null,
                "date DESC"
            )

            cursor?.use { c ->
                val bodyIndex = c.getColumnIndex("body")
                var checkedCount = 0

                while (c.moveToNext() && checkedCount < 400) {
                    checkedCount++
                    if (bodyIndex < 0) continue
                    val rawBody = c.getString(bodyIndex) ?: continue
                    val normalized = SmsParser.normalizeDigits(rawBody)

                    val hasBankKeywords = normalized.contains("واریز") ||
                            normalized.contains("برداشت") ||
                            normalized.contains("حساب")
                    val balanceMatch = Regex("""مانده\s*:?\s*([\d,]+)""").find(normalized)

                    if (hasBankKeywords && balanceMatch != null) {
                        val extractedBalance = balanceMatch.groupValues[1]
                            .replace(",", "")
                            .toLongOrNull()

                        if (extractedBalance != null) {
                            val dateMatch = Regex("""(\d{2}/\d{2}/\d{2}-\d{2}:\d{2})""").find(normalized)
                            val dateStr = dateMatch?.groupValues?.get(1) ?: "آخرین پیامک بانک"

                            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putLong(KEY_BALANCE, extractedBalance)
                                .putString(KEY_BALANCE_DATE, dateStr)
                                .apply()

                            if (showToast) {
                                Toast.makeText(
                                    this,
                                    "✅ موجودی از آخرین پیامک بانک خوانده شد: ${formatNumber(extractedBalance)}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndAskPendingTransactions() {
        if (isDialogShowing) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optBoolean("pending", false)) {
                val txId = obj.getLong("id")
                val amount = obj.getLong("amount")
                val dateStr = obj.optString("date", "امروز")
                val type = obj.optString("type", "WITHDRAW")
                showAskRealTransactionDialog(txId, amount, dateStr, type)
                break
            }
        }
    }

    // پنجره شیشه‌ای پرسش از کاربر برای پیامک‌های واقعی
    private fun showAskRealTransactionDialog(txId: Long, amount: Long, dateStr: String, type: String) {
        isDialogShowing = true
        val isWithdraw = (type == "WITHDRAW")

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = createGlassBg("#F2151C33", "#8038BDF8", dp(22).toFloat(), 3)
        }

        dialogLayout.addView(TextView(this).apply {
            text = if (isWithdraw) "✨ ثبت جزئیات خرید جدید" else "💰 ثبت منبع واریز بانکی"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(if (isWithdraw) "#38BDF8" else "#34D399"))
        })

        dialogLayout.addView(TextView(this).apply {
            text = if (isWithdraw) {
                "\nمبلغ ${formatNumber(amount)} از حساب شما برداشت شد ($dateStr).\nچه چیزی با این مبلغ خریده‌اید؟"
            } else {
                "\nمبلغ ${formatNumber(amount)} به حساب شما واریز شد ($dateStr).\nتوضیح یا منبع این درآمد چیست؟"
            }
            textSize = 14.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            setLineSpacing(10f, 1f)
        })

        val inputEdit = EditText(this).apply {
            hint = if (isWithdraw) "مثلاً: سوپرمارکت، رستوران، نانوایی..." else "مثلاً: حقوق، فروش کالا، واریز مشتری..."
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = createGlassBg("#26FFFFFF", "#4DFFFFFF", dp(14).toFloat(), 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(14), 0, dp(12))
            }
        }
        dialogLayout.addView(inputEdit)

        dialogLayout.addView(TextView(this).apply {
            text = if (isWithdraw) "بخش مورد نظر را انتخاب کنید:" else "دسته درآمد را انتخاب کنید:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(6))
        })

        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val categoriesList = if (isWithdraw) EXPENSE_CATEGORIES else INCOME_CATEGORIES
        val btnColors = if (isWithdraw) CHART_COLORS_EXPENSE else CHART_COLORS_INCOME

        categoriesList.forEachIndexed { index, categoryName ->
            val btn = Button(this).apply {
                text = categoryName
                setTextColor(Color.WHITE)
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                background = createGlassBg(
                    addAlphaToHex(btnColors[index], "44"),
                    btnColors[index],
                    dp(14).toFloat(),
                    3
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(8), 0, 0)
                }

                setOnClickListener {
                    val defaultTitle = if (isWithdraw) "خرید روزانه" else "واریز بانکی"
                    val itemTitle = inputEdit.text.toString().trim().ifEmpty { defaultTitle }
                    updateTransactionDetails(txId, itemTitle, categoryName)
                    isDialogShowing = false
                    dialog.dismiss()
                    refreshUI()
                    checkAndAskPendingTransactions()
                }
            }
            dialogLayout.addView(btn)
        }

        dialog.show()
    }

    // پنجره شیشه‌ای مخصوص حالت تست (بدون تغییر در موجودی واقعی)
    private fun showTestTransactionDialog(type: String, amount: Long, dateStr: String) {
        val isWithdraw = (type == "WITHDRAW")

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = createGlassBg("#F21E1B4B", "#99FBBF24", dp(22).toFloat(), 3)
        }

        dialogLayout.addView(TextView(this).apply {
            text = if (isWithdraw) "🧪 تست پنجره خرید (آزمایشی)" else "🧪 تست پنجره واریز (آزمایشی)"
            textSize = 17.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FBBF24"))
        })

        dialogLayout.addView(TextView(this).apply {
            text = "⚠️ این فقط یک تست است و از موجودی واقعی شما کم نمی‌کند."
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FDE047"))
            setPadding(0, dp(6), 0, dp(4))
        })

        dialogLayout.addView(TextView(this).apply {
            text = if (isWithdraw) {
                "\nفرض کنید مبلغ ${formatNumber(amount)} برداشت شده است ($dateStr).\nچه چیزی با این مبلغ خریده‌اید؟"
            } else {
                "\nفرض کنید مبلغ ${formatNumber(amount)} واریز شده است ($dateStr).\nمنبع این واریزی چیست؟"
            }
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            setLineSpacing(8f, 1f)
        })

        val inputEdit = EditText(this).apply {
            hint = "یک عنوان آزمایشی بنویسید..."
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = createGlassBg("#26FFFFFF", "#4DFFFFFF", dp(14).toFloat(), 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(12), 0, dp(12))
            }
        }
        dialogLayout.addView(inputEdit)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val categoriesList = if (isWithdraw) EXPENSE_CATEGORIES else INCOME_CATEGORIES
        val btnColors = if (isWithdraw) CHART_COLORS_EXPENSE else CHART_COLORS_INCOME

        categoriesList.forEachIndexed { index, categoryName ->
            val btn = Button(this).apply {
                text = "$categoryName (تست)"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                background = createGlassBg(
                    addAlphaToHex(btnColors[index], "44"),
                    btnColors[index],
                    dp(14).toFloat(),
                    3
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(8), 0, 0)
                }

                setOnClickListener {
                    val sampleTitle = inputEdit.text.toString().trim().ifEmpty { "مورد آزمایشی" }
                    dialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "✅ تست انجام شد: «$sampleTitle» در بخش «$categoryName» (موجودی واقعی تغییر نکرد)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            dialogLayout.addView(btn)
        }

        dialog.show()
    }

    private fun updateTransactionDetails(txId: Long, title: String, category: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getLong("id") == txId) {
                obj.put("title", title)
                obj.put("category", category)
                obj.put("pending", false)
                break
            }
        }
        prefs.edit().putString(KEY_TRANSACTIONS, arr.toString()).apply()
    }

    private fun refreshUI() {
        renderTopSubTabs()
        renderBottomNavBar()
        pageContentContainer.removeAllViews()

        when (currentTabIndex) {
            0 -> renderChartsAndAnalysisPage()
            1 -> renderTenDayCategoriesPage()
            2 -> renderTurnoverPage()
            3 -> renderToolsAndSimulatorPage()
        }
    }

    private fun renderTopSubTabs() {
        topSubTabBar.removeAllViews()
        val topTabs = listOf(
            "تحلیل و نمودارها" to 0,
            "لیست ۱۰ روزه خرج" to 1,
            "گردش حساب بانک" to 2
        )

        topTabs.forEach { (title, targetIndex) ->
            val isSelected = (currentTabIndex == targetIndex)
            val tabItemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    currentTabIndex = targetIndex
                    refreshUI()
                }
            }

            tabItemLayout.addView(TextView(this).apply {
                text = title
                textSize = 13.5f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(12), dp(6), dp(10))
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(Color.parseColor(if (isSelected) "#38BDF8" else "#94A3B8"))
            })

            tabItemLayout.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
                setBackgroundColor(Color.parseColor(if (isSelected) "#38BDF8" else "#00000000"))
            })

            topSubTabBar.addView(tabItemLayout)
        }
    }

    private fun renderBottomNavBar() {
        bottomNavBar.removeAllViews()
        val navItems = listOf(
            "📊 نمودارها",
            "🗂️ تفکیک ۱۰ روزه",
            "🔄 گردش حساب",
            "⚙️ تست پیامک"
        )

        navItems.forEachIndexed { index, label ->
            val isSelected = (index == currentTabIndex)
            val navBtn = TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#94A3B8"))
                if (isSelected) {
                    background = createGlassBg("#4D38BDF8", "#9938BDF8", dp(14).toFloat(), 2)
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    currentTabIndex = index
                    refreshUI()
                }
            }
            bottomNavBar.addView(navBtn)
        }
    }

    // =========================================================================
    // صفحه ۱: تحلیل، موجودی واقعی و نمودارها (چیدمان چرتکه با استایل شیشه‌ای)
    // =========================================================================
    private fun renderChartsAndAnalysisPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))
        val lastBalance = prefs.getLong(KEY_BALANCE, 0L)
        val lastBalanceDate = prefs.getString(KEY_BALANCE_DATE, "در انتظار خواندن پیامک...") ?: ""
        val cycleStart = prefs.getLong(KEY_CYCLE_START, System.currentTimeMillis())
        val daysPassed = ((System.currentTimeMillis() - cycleStart) / (1000 * 60 * 60 * 24)).toInt() + 1

        var totalDeposit = 0L
        var totalWithdraw = 0L
        val expenseSums = mutableMapOf<String, Long>()
        val incomeSums = mutableMapOf<String, Long>()

        EXPENSE_CATEGORIES.forEach { expenseSums[it] = 0L }
        INCOME_CATEGORIES.forEach { incomeSums[it] = 0L }

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val amount = obj.getLong("amount")
            val type = obj.getString("type")
            if (type == "DEPOSIT") {
                totalDeposit += amount
                val cat = obj.optString("category", INCOME_CATEGORIES[0])
                incomeSums[cat] = (incomeSums[cat] ?: 0L) + amount
            } else if (type == "WITHDRAW") {
                totalWithdraw += amount
                val cat = obj.optString("category", EXPENSE_CATEGORIES[0])
                expenseSums[cat] = (expenseSums[cat] ?: 0L) + amount
            }
        }

        // ۱. کپسول شیشه‌ای دوره ۱۰ روزه
        val periodPillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = createCardLayoutParams(bottomMarginDp = 18)
        }

        periodPillsRow.addView(TextView(this).apply {
            text = if (daysPassed >= 10) "🔔 دوره ۱۰ روزه تکمیل شد (روز $daysPassed)" else "📅 دوره فعال: روز $daysPassed از ۱۰"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(dp(20), dp(8), dp(20), dp(8))
            background = createGlassBg("#2938BDF8", "#8038BDF8", dp(20).toFloat(), 2)
        })
        pageContentContainer.addView(periodPillsRow)

        // ۲. کارت شیشه‌ای موجودی واقعی بانک (از آخرین پیامک گوشی)
        val bankCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassBg("#26FFFFFF", "#6638BDF8", dp(22).toFloat(), 3)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 22)
        }

        bankCard.addView(TextView(this).apply {
            text = "🏦 موجودی واقعی (از آخرین پیامک بانک)"
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
        })

        bankCard.addView(TextView(this).apply {
            text = formatNumber(lastBalance)
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
        })

        bankCard.addView(TextView(this).apply {
            text = "زمان آخرین پیامک مانده: $lastBalanceDate"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        })

        val refreshInboxBtn = Button(this).apply {
            text = "🔄 بروزرسانی مجدد موجودی از پیامک‌های گوشی"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createGlassBg("#3338BDF8", "#8038BDF8", dp(14).toFloat(), 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            )
            setOnClickListener {
                syncLatestBalanceFromSmsInbox(showToast = true)
                refreshUI()
            }
        }
        bankCard.addView(refreshInboxBtn)
        pageContentContainer.addView(bankCard)

        // ۳. کارت شیشه‌ای نمودار میله‌ای نئونی (مشابه چرتکه)
        val barChartCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassBg("#22FFFFFF", "#4DFFFFFF", dp(22).toFloat(), 2)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 22)
        }

        val barTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val totalInfoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val activeTotal = if (showExpenseInBarChart) totalWithdraw else totalDeposit
        totalInfoCol.addView(TextView(this).apply {
            text = formatNumber(activeTotal)
            textSize = 18.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        totalInfoCol.addView(TextView(this).apply {
            text = if (showExpenseInBarChart) "مجموع خرج‌کرد واقعی در این دوره" else "مجموع واریزی واقعی در این دوره"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        })
        barTopRow.addView(totalInfoCol)

        val toggleBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBg("#1AFFFFFF", "#4DFFFFFF", dp(12).toFloat(), 2)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }

        val expenseToggleBtn = TextView(this).apply {
            text = "هزینه‌ها"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(if (showExpenseInBarChart) Color.WHITE else Color.parseColor("#94A3B8"))
            if (showExpenseInBarChart) {
                background = createGlassBg("#4D38BDF8", "#9938BDF8", dp(9).toFloat(), 2)
            }
            setOnClickListener {
                showExpenseInBarChart = true
                refreshUI()
            }
        }

        val incomeToggleBtn = TextView(this).apply {
            text = "درآمدها"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(if (!showExpenseInBarChart) Color.WHITE else Color.parseColor("#94A3B8"))
            if (!showExpenseInBarChart) {
                background = createGlassBg("#4D34D399", "#9934D399", dp(9).toFloat(), 2)
            }
            setOnClickListener {
                showExpenseInBarChart = false
                refreshUI()
            }
        }

        toggleBox.addView(expenseToggleBtn)
        toggleBox.addView(incomeToggleBtn)
        barTopRow.addView(toggleBox)
        barChartCard.addView(barTopRow)

        val barValues = if (showExpenseInBarChart) {
            EXPENSE_CATEGORIES.map { (expenseSums[it] ?: 0L).toFloat() }
        } else {
            INCOME_CATEGORIES.map { (incomeSums[it] ?: 0L).toFloat() }
        }
        val barLabels = if (showExpenseInBarChart) EXPENSE_CATEGORIES else INCOME_CATEGORIES

        val barChartView = GlassBarChartView(this, barValues, barLabels, showExpenseInBarChart).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(165)
            ).apply {
                setMargins(0, dp(16), 0, 0)
            }
        }
        barChartCard.addView(barChartView)
        pageContentContainer.addView(barChartCard)

        // ۴. کارت شیشه‌ای نمودار نیم‌دایره هزینه‌ها
        val expensePieCard = buildGlassArchChartCard(
            badgeActiveText = "نمودار دایره‌ای هزینه‌ها",
            badgeInactiveText = "۴ بخش اصلی",
            borderHex = "#66FF5E8E",
            categories = EXPENSE_CATEGORIES,
            sumsMap = expenseSums,
            totalAmount = totalWithdraw,
            colorsHex = CHART_COLORS_EXPENSE,
            emptyHint = "در حال حاضر خرجی در این دوره ثبت نشده است."
        )
        pageContentContainer.addView(expensePieCard)

        // ۵. کارت شیشه‌ای نمودار نیم‌دایره درآمدها
        val incomePieCard = buildGlassArchChartCard(
            badgeActiveText = "نمودار دایره‌ای درآمدها",
            badgeInactiveText = "منابع واریز",
            borderHex = "#6634D399",
            categories = INCOME_CATEGORIES,
            sumsMap = incomeSums,
            totalAmount = totalDeposit,
            colorsHex = CHART_COLORS_INCOME,
            emptyHint = "در حال حاضر واریزی در این دوره ثبت نشده است."
        )
        pageContentContainer.addView(incomePieCard)
    }

    private fun buildGlassArchChartCard(
        badgeActiveText: String,
        badgeInactiveText: String,
        borderHex: String,
        categories: List<String>,
        sumsMap: Map<String, Long>,
        totalAmount: Long,
        colorsHex: List<String>,
        emptyHint: String
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = createGlassBg("#22FFFFFF", borderHex, dp(22).toFloat(), 3)
            setPadding(dp(18), dp(18), dp(18), dp(20))
            layoutParams = createCardLayoutParams(bottomMarginDp = 22)
        }

        val headerPillBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBg("#1AFFFFFF", "#4DFFFFFF", dp(12).toFloat(), 2)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(14))
            }
        }

        headerPillBox.addView(TextView(this).apply {
            text = badgeActiveText
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = createGlassBg("#4D38BDF8", "#9938BDF8", dp(9).toFloat(), 2)
        })

        headerPillBox.addView(TextView(this).apply {
            text = badgeInactiveText
            textSize = 12.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        })

        card.addView(headerPillBox)

        val slices = categories.mapIndexed { idx, cat ->
            ArchSlice(
                value = (sumsMap[cat] ?: 0L).toFloat(),
                color = Color.parseColor(colorsHex[idx])
            )
        }

        val archChartView = GlassArchChartView(this, slices).apply {
            layoutParams = LinearLayout.LayoutParams(dp(240), dp(145))
        }
        card.addView(archChartView)

        card.addView(TextView(this).apply {
            text = if (totalAmount > 0) {
                "جمع کل: ${formatNumber(totalAmount)}  |  تفکیک هر بخش در زیر:"
            } else {
                emptyHint
            }
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(14))
        })

        categories.forEachIndexed { idx, cat ->
            val amount = sumsMap[cat] ?: 0L
            val percent = if (totalAmount > 0) ((amount * 100f) / totalAmount).toInt() else 0

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createGlassBg("#1AFFFFFF", addAlphaToHex(colorsHex[idx], "55"), dp(12).toFloat(), 2)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(5), 0, dp(5))
                }
            }

            row.addView(View(this).apply {
                background = createGlassBg(colorsHex[idx], "#FFFFFF", dp(6).toFloat(), 1)
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12))
            })

            row.addView(TextView(this).apply {
                text = "$cat ($percent٪)"
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dp(10), 0, dp(10), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(TextView(this).apply {
                text = formatNumber(amount)
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(colorsHex[idx]))
            })

            card.addView(row)
        }

        return card
    }

    // =========================================================================
    // صفحه ۲: لیست خرج‌کردهای ۱۰ روزه به تفکیک ۴ بخش (کارت‌های شیشه‌ای)
    // =========================================================================
    private fun renderTenDayCategoriesPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))

        val categorySums = mutableMapOf<String, Long>()
        val categoryItems = mutableMapOf<String, MutableList<String>>()

        EXPENSE_CATEGORIES.forEach {
            categorySums[it] = 0L
            categoryItems[it] = mutableListOf()
        }

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("type") == "WITHDRAW") {
                val amount = obj.getLong("amount")
                val cat = obj.optString("category", EXPENSE_CATEGORIES[0])
                val title = obj.optString("title", "ثبت نشده")
                val date = obj.optString("date", "")
                if (categorySums.containsKey(cat)) {
                    categorySums[cat] = (categorySums[cat] ?: 0L) + amount
                    categoryItems[cat]?.add("• $title : ${formatNumber(amount)} ($date)")
                }
            }
        }

        EXPENSE_CATEGORIES.forEachIndexed { idx, cat ->
            val accentHex = CHART_COLORS_EXPENSE[idx]
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createGlassBg("#22FFFFFF", addAlphaToHex(accentHex, "66"), dp(20).toFloat(), 3)
                setPadding(dp(18), dp(18), dp(18), dp(18))
                layoutParams = createCardLayoutParams(bottomMarginDp = 20)
            }

            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            titleRow.addView(View(this).apply {
                background = createGlassBg(accentHex, "#FFFFFF", dp(5).toFloat(), 1)
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(24))
            })

            val sum = categorySums[cat] ?: 0L
            titleRow.addView(TextView(this).apply {
                text = cat
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dp(10), 0, dp(10), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            titleRow.addView(TextView(this).apply {
                text = formatNumber(sum)
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(accentHex))
            })

            card.addView(titleRow)

            card.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    setMargins(0, dp(12), 0, dp(12))
                }
                setBackgroundColor(Color.parseColor("#26FFFFFF"))
            })

            val items = categoryItems[cat] ?: emptyList()
            val itemsText = if (items.isEmpty()) {
                "هیچ موردی در این دوره ۱۰ روزه ثبت نشده است."
            } else {
                items.joinToString("\n\n")
            }

            card.addView(TextView(this).apply {
                text = itemsText
                textSize = 13.5f
                setTextColor(Color.parseColor("#CBD5E1"))
                setLineSpacing(8f, 1f)
            })

            pageContentContainer.addView(card)
        }
    }

    // =========================================================================
    // صفحه ۳: گردش حساب واقعی (کارت‌های شیشه‌ای)
    // =========================================================================
    private fun renderTurnoverPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))

        if (arr.length() == 0) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createGlassBg("#22FFFFFF", "#4DFFFFFF", dp(20).toFloat(), 2)
                setPadding(dp(22), dp(22), dp(22), dp(22))
                layoutParams = createCardLayoutParams(bottomMarginDp = 18)
            }
            emptyCard.addView(TextView(this).apply {
                text = "هنوز تراکنش جدیدی در این دوره ثبت نشده است."
                textSize = 14f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
            })
            pageContentContainer.addView(emptyCard)
            return
        }

        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.getJSONObject(i)
            val type = obj.getString("type")
            val amount = obj.getLong("amount")
            val date = obj.optString("date", "")
            val title = obj.optString("title", if (type == "DEPOSIT") "واریز بانکی" else "خرید")
            val cat = obj.optString("category", "")
            val txBalance = obj.optLong("balance", -1L)

            val isDeposit = (type == "DEPOSIT")
            val borderHex = if (isDeposit) "#6634D399" else "#66FF5E8E"

            val txCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createGlassBg("#22FFFFFF", borderHex, dp(18).toFloat(), 2)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                layoutParams = createCardLayoutParams(bottomMarginDp = 14)
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            topRow.addView(TextView(this).apply {
                text = title
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            topRow.addView(TextView(this).apply {
                text = if (isDeposit) "+ ${formatNumber(amount)}" else "- ${formatNumber(amount)}"
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(if (isDeposit) "#34D399" else "#FF5E8E"))
            })

            txCard.addView(topRow)

            txCard.addView(TextView(this).apply {
                text = "بخش: $cat   |   تاریخ: $date"
                textSize = 13f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(8), 0, dp(4))
            })

            if (txBalance >= 0) {
                txCard.addView(TextView(this).apply {
                    text = "مانده حساب در پیامک: ${formatNumber(txBalance)}"
                    textSize = 12.5f
                    setTextColor(Color.parseColor("#38BDF8"))
                })
            }

            pageContentContainer.addView(txCard)
        }
    }

    // =========================================================================
    // صفحه ۴: ابزارها و تست (صرفاً آزمایشی بدون تغییر در موجودی واقعی)
    // =========================================================================
    private fun renderToolsAndSimulatorPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassBg("#24FFFFFF", "#6638BDF8", dp(20).toFloat(), 2)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 20)
        }

        infoCard.addView(TextView(this).apply {
            text = "🧪 بخش تست عملکرد (بدون تاثیر روی موجودی)"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
        })

        infoCard.addView(TextView(this).apply {
            text = "\n• موجودی واقعی شما به صورت خودکار از آخرین پیامک بانک در گوشی خوانده می‌شود.\n• دکمه‌های تست زیر فقط برای نمایش آزمایشی پنجره سوال و اعلان هستند و هیچ مبلغی را از موجودی واقعی شما کم یا زیاد نمی‌کنند."
            textSize = 13.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            setLineSpacing(8f, 1f)
        })
        pageContentContainer.addView(infoCard)

        val testWithdrawBtn = Button(this).apply {
            text = "🧪 تست پنجره پیامک برداشت (بدون کسر از موجودی)"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            background = createGlassBg("#4D0284C7", "#9938BDF8", dp(16).toFloat(), 3)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = createCardLayoutParams(bottomMarginDp = 16)
            setOnClickListener {
                val sampleWithdrawSms = """
                    حساب ********
                    برداشت1,511,000
                    مانده712,744
                    05/08/21-10:50
                """.trimIndent()
                SmsParser.processBankSms(this@MainActivity, sampleWithdrawSms, isTest = true)
            }
        }
        pageContentContainer.addView(testWithdrawBtn)

        val testDepositBtn = Button(this).apply {
            text = "🧪 تست پنجره پیامک واریز (بدون تغییر موجودی)"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            background = createGlassBg("#4D059669", "#9934D399", dp(16).toFloat(), 3)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = createCardLayoutParams(bottomMarginDp = 24)
            setOnClickListener {
                val sampleDepositSms = """
                    حساب ********
                    واریز1,250,000
                    مانده1,962,744
                    05/08/21-19:57
                """.trimIndent()
                SmsParser.processBankSms(this@MainActivity, sampleDepositSms, isTest = true)
            }
        }
        pageContentContainer.addView(testDepositBtn)

        val resetBtn = Button(this).apply {
            text = "🔄 شروع دوره ۱۰ روزه جدید (پاک کردن لیست خرج‌ها)"
            setTextColor(Color.parseColor("#FCA5A5"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            background = createGlassBg("#33DC2626", "#99F87171", dp(16).toFloat(), 3)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = createCardLayoutParams(bottomMarginDp = 20)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("شروع دوره ۱۰ روزه جدید")
                    .setMessage("آیا مطمئن هستید که می‌خواهید لیست خرج‌کردهای دوره فعلی را پاک کنید و ۱۰ روز جدید را آغاز نمایید؟ (موجودی بانک شما از آخرین پیامک حفظ می‌شود)")
                    .setPositiveButton("بله، شروع شود") { _, _ ->
                        prefs.edit()
                            .putString(KEY_TRANSACTIONS, "[]")
                            .putLong(KEY_CYCLE_START, System.currentTimeMillis())
                            .apply()
                        syncLatestBalanceFromSmsInbox(showToast = false)
                        refreshUI()
                    }
                    .setNegativeButton("انصراف", null)
                    .show()
            }
        }
        pageContentContainer.addView(resetBtn)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun createCardLayoutParams(bottomMarginDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dp(bottomMarginDp))
        }
    }

    private fun formatNumber(num: Long): String {
        return NumberFormat.getNumberInstance(Locale.US).format(num)
    }

    private fun addAlphaToHex(hex: String, alphaHex: String): String {
        val clean = hex.removePrefix("#")
        return if (clean.length == 6) "#$alphaHex$clean" else hex
    }

    // تابع ساخت کادرهای شیشه‌ای (Glassmorphism) با حاشیه براق
    private fun createGlassBg(
        fillHexWithAlpha: String,
        borderHexWithAlpha: String,
        radius: Float,
        strokeWidth: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor(fillHexWithAlpha))
            setStroke(strokeWidth, Color.parseColor(borderHexWithAlpha))
        }
    }
}

// =========================================================================
// ویوی نمودار میله‌ای نئونی شیشه‌ای (به سبک چرتکه)
// =========================================================================
class GlassBarChartView(
    context: Context,
    private val values: List<Float>,
    private val labels: List<String>,
    private val isExpenseMode: Boolean
) : View(context) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26FFFFFF")
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val bottomSpace = 48f
        val chartHeight = h - bottomSpace - 16f

        for (i in 0..3) {
            val y = 16f + (chartHeight / 3f) * i
            canvas.drawLine(16f, y, w - 16f, y, gridPaint)
        }

        if (values.isEmpty()) return
        val maxVal = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val count = values.size
        val slotWidth = (w - 32f) / count
        val barWidth = min(slotWidth * 0.38f, 44f)

        val activeBarColor = if (isExpenseMode) Color.parseColor("#38BDF8") else Color.parseColor("#34D399")

        for (i in 0 until count) {
            val cx = 16f + slotWidth * i + slotWidth / 2f
            val ratio = if (values[i] > 0f) (values[i] / maxVal).coerceAtLeast(0.08f) else 0.05f
            val barTop = 16f + chartHeight * (1f - ratio)
            val barBottom = 16f + chartHeight

            barPaint.color = if (values[i] > 0f) activeBarColor else Color.parseColor("#33FFFFFF")
            rectF.set(cx - barWidth / 2f, barTop, cx + barWidth / 2f, barBottom)
            canvas.drawRoundRect(rectF, 10f, 10f, barPaint)

            val shortLabel = labels.getOrNull(i)?.take(10) ?: ""
            canvas.drawText(shortLabel, cx, h - 12f, textPaint)
        }
    }
}

// =========================================================================
// ویوی نمودار قوسی / نیم‌دایره نئونی شیشه‌ای (به سبک چرتکه)
// =========================================================================
data class ArchSlice(val value: Float, val color: Int)

class GlassArchChartView(
    context: Context,
    private val slices: List<ArchSlice>
) : View(context) {

    private val archPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val emptyArchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#26FFFFFF")
    }

    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = min(w, h) * 0.22f
        val radius = min(w / 2f, h * 0.85f) - stroke

        val cx = w / 2f
        val cy = h * 0.88f

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)
        archPaint.strokeWidth = stroke
        emptyArchPaint.strokeWidth = stroke

        val startBaseAngle = 170f
        val totalSweepAngle = 200f
        canvas.drawArc(rectF, startBaseAngle, totalSweepAngle, false, emptyArchPaint)

        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return

        var currentAngle = startBaseAngle
        for (slice in slices) {
            if (slice.value <= 0f) continue
            val sweep = (slice.value / total) * totalSweepAngle
            archPaint.color = slice.color
            canvas.drawArc(rectF, currentAngle, sweep, false, archPaint)
            currentAngle += sweep
        }
    }
}

// =========================================================================
// دریافت خودکار پیامک‌های واقعی جدید از بانک
// =========================================================================
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val fullText = StringBuilder()
            for (msg in messages) {
                fullText.append(msg.messageBody)
            }
            SmsParser.processBankSms(context, fullText.toString(), isTest = false)
        }
    }
}

object SmsParser {
    fun processBankSms(context: Context, rawText: String, isTest: Boolean = false) {
        val text = normalizeDigits(rawText)

        val depositMatch = Regex("""واریز\s*:?\s*([\d,]+)""").find(text)
        val withdrawMatch = Regex("""برداشت\s*:?\s*([\d,]+)""").find(text)
        val balanceMatch = Regex("""مانده\s*:?\s*([\d,]+)""").find(text)
        val dateMatch = Regex("""(\d{2}/\d{2}/\d{2}-\d{2}:\d{2})""").find(text)

        if (depositMatch == null && withdrawMatch == null) return

        val dateStr = dateMatch?.groupValues?.get(1) ?: "امروز"

        // اگر حالت تست بود، به هیچ وجه روی موجودی یا لیست واقعی تاثیر نگذارد
        if (isTest) {
            if (depositMatch != null) {
                val amount = depositMatch.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
                sendNotification(
                    context,
                    "[تست] واریز بانکی",
                    "تست پیامک واریز به مبلغ ${depositMatch.groupValues[1]} (بدون تغییر در موجودی واقعی)"
                )
                MainActivity.onTestSmsReceived?.invoke("DEPOSIT", amount, dateStr)
            } else if (withdrawMatch != null) {
                val amount = withdrawMatch.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
                sendNotification(
                    context,
                    "[تست] برداشت بانکی: چه چیزی خریدید؟",
                    "تست پیامک برداشت به مبلغ ${withdrawMatch.groupValues[1]} (بدون کسر از موجودی واقعی)"
                )
                MainActivity.onTestSmsReceived?.invoke("WITHDRAW", amount, dateStr)
            }
            return
        }

        // پردازش پیامک واقعی بانک
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(MainActivity.KEY_TRANSACTIONS, "[]"))
        val balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()

        val editor = prefs.edit()
        if (balance != null) {
            editor.putLong(MainActivity.KEY_BALANCE, balance)
            editor.putString(MainActivity.KEY_BALANCE_DATE, dateStr)
        }

        if (depositMatch != null) {
            val amount = depositMatch.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
            if (amount > 0) {
                val obj = JSONObject().apply {
                    put("id", System.currentTimeMillis())
                    put("type", "DEPOSIT")
                    put("amount", amount)
                    put("date", dateStr)
                    put("balance", balance ?: -1L)
                    put("title", "واریز بانکی")
                    put("category", MainActivity.INCOME_CATEGORIES[0])
                    put("pending", true)
                }
                arr.put(obj)
                editor.putString(MainActivity.KEY_TRANSACTIONS, arr.toString()).apply()
                sendNotification(
                    context,
                    "واریز بانکی: منبع درآمد چیست؟",
                    "مبلغ ${depositMatch.groupValues[1]} واریز شد. ضربه بزنید و دسته درآمد را مشخص کنید."
                )
            }
        } else if (withdrawMatch != null) {
            val amount = withdrawMatch.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
            if (amount > 0) {
                val obj = JSONObject().apply {
                    put("id", System.currentTimeMillis())
                    put("type", "WITHDRAW")
                    put("amount", amount)
                    put("date", dateStr)
                    put("balance", balance ?: -1L)
                    put("title", "ثبت نشده")
                    put("category", MainActivity.EXPENSE_CATEGORIES[0])
                    put("pending", true)
                }
                arr.put(obj)
                editor.putString(MainActivity.KEY_TRANSACTIONS, arr.toString()).apply()
                sendNotification(
                    context,
                    "برداشت بانکی: چه چیزی خریدید؟",
                    "مبلغ ${withdrawMatch.groupValues[1]} برداشت شد. ضربه بزنید و دسته‌بندی را مشخص کنید."
                )
            }
        }

        MainActivity.onSmsReceivedUIUpdate?.invoke()
    }

    fun normalizeDigits(input: String): String {
        val persian = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabic = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        var res = input
        for (i in 0..9) {
            res = res.replace(persian[i], '0' + i)
            res = res.replace(arabic[i], '0' + i)
        }
        return res.replace('٬', ',')
    }

    private fun sendNotification(context: Context, title: String, message: String) {
        val channelId = "bank_tracker_channel"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "اعلان تراکنش‌ها", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, piFlags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, channelId)
        } else {
            android.app.Notification.Builder(context)
        }

        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        nm.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
