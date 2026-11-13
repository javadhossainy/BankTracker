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
        const val PREFS_NAME = "BankTrackerPrefs"
        const val KEY_TRANSACTIONS = "transactions"
        const val KEY_BALANCE = "last_balance"
        const val KEY_CYCLE_START = "cycle_start_ms"

        // ۴ دسته‌بندی اصلی هزینه‌ها طبق خواسته شما
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

        // پالت رنگی الهام‌گرفته از نمودارهای برنامه چرتکه
        val CHART_COLORS_EXPENSE = listOf("#F46A8E", "#28C3D4", "#3B82F6", "#F6C844")
        val CHART_COLORS_INCOME = listOf("#10B981", "#1D63B8", "#38BDF8", "#F59E0B")
    }

    private lateinit var rootLayout: LinearLayout
    private lateinit var topSubTabBar: LinearLayout
    private lateinit var pageContentContainer: LinearLayout
    private lateinit var bottomNavBar: LinearLayout

    // تب فعال پایین صفحه: 0 = نمودارها | 1 = تفکیک ۱۰ روزه | 2 = گردش حساب | 3 = ابزارها
    private var currentTabIndex = 0
    // حالت نمودار میله‌ای بالای صفحه: true = خرید/هزینه | false = فروش/درآمد
    private var showExpenseInBarChart = true
    private var isDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        window.statusBarColor = Color.parseColor("#1856A8")

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F6FC")) // پس‌زمینه روشن مشابه چرتکه
        }

        // ۱. نوار هدر بالایی (سفید و آبی)
        val topAppBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        val appLogoBadge = TextView(this).apply {
            text = "٪"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = createRoundedBg("#1D63B8", dp(10).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }
        topAppBar.addView(appLogoBadge)

        val appTitle = TextView(this).apply {
            text = "مدیریت مالی و خرج ۱۰ روزه"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topAppBar.addView(appTitle)

        val quickTestBadge = TextView(this).apply {
            text = "تست پیامک"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1D63B8"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = createOutlinedBg("#EBF3FE", "#1D63B8", dp(14).toFloat(), 2)
            setOnClickListener {
                currentTabIndex = 3
                refreshUI()
            }
        }
        topAppBar.addView(quickTestBadge)
        rootLayout.addView(topAppBar)

        // ۲. تب‌های خط‌دار بالای صفحه (مشابه «تحلیل و نمودارها | مدیریت مالی» در چرتکه)
        topSubTabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
        }
        rootLayout.addView(topSubTabBar)

        // خط جداکننده ظریف زیر هدر
        rootLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(Color.parseColor("#DCE4F0"))
        })

        // ۳. بخش محتوای قابل اسکرول با فاصله‌گذاری استاندارد
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

        // ۴. خط جداکننده و نوار ناوبری پایین صفحه (۴ تب اصلی)
        rootLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(Color.parseColor("#DCE4F0"))
        })

        bottomNavBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        rootLayout.addView(bottomNavBar)

        setContentView(rootLayout)

        initCycleIfNeeded()
        requestNeededPermissions()
    }

    override fun onResume() {
        super.onResume()
        onSmsReceivedUIUpdate = {
            runOnUiThread {
                refreshUI()
                checkAndAskPendingTransactions()
            }
        }
        refreshUI()
        checkAndAskPendingTransactions()
    }

    override fun onPause() {
        super.onPause()
        onSmsReceivedUIUpdate = null
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

    // بررسی تراکنش‌های جدیدی که منتظر پاسخ کاربر هستند
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
                showAskTransactionDialog(txId, amount, dateStr, type)
                break
            }
        }
    }

    // پنجره سفید و آبی پرسش از کاربر: «چه چیزی با این خریده‌اید؟»
    private fun showAskTransactionDialog(txId: Long, amount: Long, dateStr: String, type: String) {
        isDialogShowing = true
        val isWithdraw = (type == "WITHDRAW")

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = createOutlinedBg("#FFFFFF", "#DCE4F0", dp(20).toFloat(), 3)
        }

        val dialogTitle = TextView(this).apply {
            text = if (isWithdraw) "ثبت جزئیات خرید جدید" else "ثبت منبع واریز بانکی"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1D63B8"))
        }
        dialogLayout.addView(dialogTitle)

        val infoText = TextView(this).apply {
            text = if (isWithdraw) {
                "\nمبلغ ${formatNumber(amount)} از حساب شما برداشت شد ($dateStr).\nچه چیزی با این مبلغ خریده‌اید؟"
            } else {
                "\nمبلغ ${formatNumber(amount)} به حساب شما واریز شد ($dateStr).\nتوضیح یا منبع این درآمد چیست؟"
            }
            textSize = 14.5f
            setTextColor(Color.parseColor("#334155"))
            setLineSpacing(10f, 1f)
        }
        dialogLayout.addView(infoText)

        val inputEdit = EditText(this).apply {
            hint = if (isWithdraw) "مثلاً: سوپرمارکت، رستوران، پوشاک..." else "مثلاً: حقوق، فروش کالا، واریز مشتری..."
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14.5f
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = createOutlinedBg("#F8FAFC", "#CBD5E1", dp(12).toFloat(), 2)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, dp(14), 0, dp(12))
            layoutParams = params
        }
        dialogLayout.addView(inputEdit)

        val catTitle = TextView(this).apply {
            text = if (isWithdraw) "بخش مورد نظر را انتخاب کنید:" else "دسته درآمد را انتخاب کنید:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, dp(4), 0, dp(6))
        }
        dialogLayout.addView(catTitle)

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
                background = createRoundedBg(btnColors[index], dp(12).toFloat())
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, dp(8), 0, 0)
                layoutParams = params

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

    // رندر تب‌های خط‌دار بالای صفحه (مشابه چرتکه)
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
                setTextColor(Color.parseColor(if (isSelected) "#1D63B8" else "#64748B"))
            })

            // خط آبی ضخیم زیر تب فعال
            tabItemLayout.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
                setBackgroundColor(Color.parseColor(if (isSelected) "#1D63B8" else "#00000000"))
            })

            topSubTabBar.addView(tabItemLayout)
        }
    }

    // رندر نوار ناوبری ۴ بخشی پایین صفحه
    private fun renderBottomNavBar() {
        bottomNavBar.removeAllViews()
        val navItems = listOf(
            "📊 نمودارها",
            "🗂️ تفکیک ۱۰ روزه",
            "🔄 گردش حساب",
            "⚙️ ابزارها"
        )

        navItems.forEachIndexed { index, label ->
            val isSelected = (index == currentTabIndex)
            val navBtn = TextView(this).apply {
                text = label
                textSize = 12.5f
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(Color.parseColor(if (isSelected) "#1D63B8" else "#64748B"))
                if (isSelected) {
                    background = createRoundedBg("#EBF3FE", dp(12).toFloat())
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
    // صفحه ۱: تحلیل و نمودارها (مشابه صفحه وسط و چپ تصویر چرتکه)
    // =========================================================================
    private fun renderChartsAndAnalysisPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))
        val lastBalance = prefs.getLong(KEY_BALANCE, 0L)
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

        // ۱. نوار کپسولی وضعیت دوره ۱۰ روزه (مشابه کپسول‌های سال مالی در چرتکه)
        val periodPillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = createCardLayoutParams(bottomMarginDp = 18)
        }

        val currentCyclePill = TextView(this).apply {
            text = if (daysPassed >= 10) "🔔 دوره ۱۰ روزه تکمیل شد (روز $daysPassed)" else "📅 دوره فعال: روز $daysPassed از ۱۰"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1D63B8"))
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = createOutlinedBg("#EBF3FE", "#93C5FD", dp(20).toFloat(), 2)
        }
        periodPillsRow.addView(currentCyclePill)
        pageContentContainer.addView(periodPillsRow)

        // ۲. کارت بانک و موجودی حساب (مشابه کارت «بانک سرمایه / موجودی حساب» در چرتکه)
        val bankCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createOutlinedBg("#FFFFFF", "#DCE4F0", dp(18).toFloat(), 2)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 22)
        }

        val bankHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bankHeaderRow.addView(TextView(this).apply {
            text = "🏦 حساب بانکی متصل به پیامک"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        bankHeaderRow.addView(TextView(this).apply {
            text = "حساب ********"
            textSize = 12.5f
            setTextColor(Color.parseColor("#64748B"))
        })
        bankCard.addView(bankHeaderRow)

        bankCard.addView(TextView(this).apply {
            text = "${formatNumber(lastBalance)} ریال / تومان"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(4))
        })

        bankCard.addView(TextView(this).apply {
            text = "آخرین موجودی استخراج‌شده از پیامک بانک"
            textSize = 12.5f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        // دکمه‌های «+ واریز» و «- برداشت» مشابه کارت بانک چرتکه
        val bankActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val depositQuickBtn = Button(this).apply {
            text = "+ ثبت / تست واریز"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createRoundedBg("#1D63B8", dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                setMargins(0, 0, 0, 0)
            }
            setOnClickListener {
                val sampleDepositSms = """
                    حساب ********
                    واریز1,250,000
                    مانده1,962,744
                    05/08/21-19:57
                """.trimIndent()
                SmsParser.processBankSms(this@MainActivity, sampleDepositSms)
            }
        }

        val spaceView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(1))
        }

        val withdrawQuickBtn = Button(this).apply {
            text = "- ثبت / تست برداشت"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1D63B8"))
            background = createOutlinedBg("#FFFFFF", "#1D63B8", dp(12).toFloat(), 2)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            setOnClickListener {
                val sampleWithdrawSms = """
                    حساب ********
                    برداشت1,511,000
                    مانده712,744
                    05/08/21-10:50
                """.trimIndent()
                SmsParser.processBankSms(this@MainActivity, sampleWithdrawSms)
            }
        }

        bankActionRow.addView(depositQuickBtn)
        bankActionRow.addView(spaceView)
        bankActionRow.addView(withdrawQuickBtn)
        bankCard.addView(bankActionRow)

        pageContentContainer.addView(bankCard)

        // ۳. کارت نمودار میله‌ای آبی‌رنگ (مشابه کارت بالای صفحه وسط چرتکه)
        val barChartCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createOutlinedBg("#FFFFFF", "#DCE4F0", dp(18).toFloat(), 2)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 22)
        }

        val barTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // جمع مبلغ در سمت راست و دکمه تغییر حالت (خرید | فروش/واریز)
        val totalInfoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val activeTotal = if (showExpenseInBarChart) totalWithdraw else totalDeposit
        totalInfoCol.addView(TextView(this).apply {
            text = formatNumber(activeTotal)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        })
        totalInfoCol.addView(TextView(this).apply {
            text = if (showExpenseInBarChart) "مجموع خرج‌کرد در این دوره" else "مجموع واریزی در این دوره"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        })
        barTopRow.addView(totalInfoCol)

        // سوییچ دوحالته «خرید (هزینه) | واریز (درآمد)» مشابه چرتکه
        val toggleBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createOutlinedBg("#F1F5F9", "#CBD5E1", dp(10).toFloat(), 1)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }

        val expenseToggleBtn = TextView(this).apply {
            text = "هزینه‌ها"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(if (showExpenseInBarChart) Color.WHITE else Color.parseColor("#475569"))
            if (showExpenseInBarChart) {
                background = createRoundedBg("#1D63B8", dp(8).toFloat())
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
            setTextColor(if (!showExpenseInBarChart) Color.WHITE else Color.parseColor("#475569"))
            if (!showExpenseInBarChart) {
                background = createRoundedBg("#1D63B8", dp(8).toFloat())
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

        // مقادیر میله‌ها بر اساس ۴ دسته + روزهای دوره
        val barValues = if (showExpenseInBarChart) {
            EXPENSE_CATEGORIES.map { (expenseSums[it] ?: 0L).toFloat() }
        } else {
            INCOME_CATEGORIES.map { (incomeSums[it] ?: 0L).toFloat() }
        }
        val barLabels = if (showExpenseInBarChart) EXPENSE_CATEGORIES else INCOME_CATEGORIES

        val barChartView = ChortkeBarChartView(this, barValues, barLabels).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(165)
            ).apply {
                setMargins(0, dp(16), 0, 0)
            }
        }
        barChartCard.addView(barChartView)
        pageContentContainer.addView(barChartCard)

        // ۴. کارت نمودار دایره‌ای (نیم‌دایره چرتکه) برای هزینه‌ها
        val expensePieCard = buildChortkeArchChartCard(
            badgeActiveText = "نمودار دایره‌ای هزینه‌ها",
            badgeInactiveText = "۴ بخش اصلی",
            categories = EXPENSE_CATEGORIES,
            sumsMap = expenseSums,
            totalAmount = totalWithdraw,
            colorsHex = CHART_COLORS_EXPENSE,
            emptyHint = "هنوز خرجی ثبت نشده است. با دکمه «ثبت / تست برداشت» آزمایش کنید."
        )
        pageContentContainer.addView(expensePieCard)

        // ۵. کارت نمودار دایره‌ای (نیم‌دایره چرتکه) برای درآمدها
        val incomePieCard = buildChortkeArchChartCard(
            badgeActiveText = "نمودار دایره‌ای درآمدها",
            badgeInactiveText = "منابع واریز",
            categories = INCOME_CATEGORIES,
            sumsMap = incomeSums,
            totalAmount = totalDeposit,
            colorsHex = CHART_COLORS_INCOME,
            emptyHint = "هنوز واریزی ثبت نشده است. با دکمه «ثبت / تست واریز» آزمایش کنید."
        )
        pageContentContainer.addView(incomePieCard)
    }

    // ساخت کارت نمودار نیم‌دایره/حلقه‌ای دقیقاً به سبک کارت پایین چرتکه
    private fun buildChortkeArchChartCard(
        badgeActiveText: String,
        badgeInactiveText: String,
        categories: List<String>,
        sumsMap: Map<String, Long>,
        totalAmount: Long,
        colorsHex: List<String>,
        emptyHint: String
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = createOutlinedBg("#FFFFFF", "#DCE4F0", dp(18).toFloat(), 2)
            setPadding(dp(18), dp(18), dp(18), dp(20))
            layoutParams = createCardLayoutParams(bottomMarginDp = 22)
        }

        // کپسول دوحالته بالای نمودار (مشابه «بیشترین خرید | بیشترین فروش» در چرتکه)
        val headerPillBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createOutlinedBg("#F1F5F9", "#DCE4F0", dp(10).toFloat(), 1)
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
            background = createRoundedBg("#1D63B8", dp(8).toFloat())
        })

        headerPillBox.addView(TextView(this).apply {
            text = badgeInactiveText
            textSize = 12.5f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        })

        card.addView(headerPillBox)

        val slices = categories.mapIndexed { idx, cat ->
            ArchSlice(
                value = (sumsMap[cat] ?: 0L).toFloat(),
                color = Color.parseColor(colorsHex[idx])
            )
        }

        // ویوی نمودار قوسی/نیم‌دایره مشابه چرتکه
        val archChartView = ChortkeArchChartView(this, slices).apply {
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
            setTextColor(Color.parseColor("#475569"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(14))
        })

        // جدول راهنمای رنگ‌ها، درصدها و مبالغ هر بخش با فاصله‌گذاری تمیز
        categories.forEachIndexed { idx, cat ->
            val amount = sumsMap[cat] ?: 0L
            val percent = if (totalAmount > 0) ((amount * 100f) / totalAmount).toInt() else 0

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createOutlinedBg("#F8FAFC", "#E2E8F0", dp(10).toFloat(), 1)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(5), 0, dp(5))
                }
            }

            // نقطه رنگی
            row.addView(View(this).apply {
                background = createRoundedBg(colorsHex[idx], dp(6).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12))
            })

            row.addView(TextView(this).apply {
                text = "$cat ($percent٪)"
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1E293B"))
                setPadding(dp(10), 0, dp(10), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(TextView(this).apply {
                text = "${formatNumber(amount)} تومان"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1D63B8"))
            })

            card.addView(row)
        }

        return card
    }

    // =========================================================================
    // صفحه ۲: لیست خرج‌کردهای ۱۰ روزه به تفکیک ۴ بخش
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
                    categoryItems[cat]?.add("• $title : ${formatNumber(amount)} تومان ($date)")
                }
            }
        }

        EXPENSE_CATEGORIES.forEachIndexed { idx, cat ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createOutlinedBg("#FFFFFF", "#DCE4F0", dp(16).toFloat(), 2)
                setPadding(dp(18), dp(18), dp(18), dp(18))
                layoutParams = createCardLayoutParams(bottomMarginDp = 20)
            }

            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            titleRow.addView(View(this).apply {
                background = createRoundedBg(CHART_COLORS_EXPENSE[idx], dp(5).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(24))
            })

            val sum = categorySums[cat] ?: 0L
            titleRow.addView(TextView(this).apply {
                text = cat
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1E293B"))
                setPadding(dp(10), 0, dp(10), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            titleRow.addView(TextView(this).apply {
                text = "${formatNumber(sum)} تومان"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1D63B8"))
            })

            card.addView(titleRow)

            card.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    setMargins(0, dp(12), 0, dp(12))
                }
                setBackgroundColor(Color.parseColor("#F1F5F9"))
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
                setTextColor(Color.parseColor("#475569"))
                setLineSpacing(8f, 1f)
            })

            pageContentContainer.addView(card)
        }
    }

    // =========================================================================
    // صفحه ۳: گردش حساب (لیست تراکنش‌های اخیر به سبک کارت‌های سفید چرتکه)
    // =========================================================================
    private fun renderTurnoverPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))

        if (arr.length() == 0) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createOutlinedBg("#FFFFFF", "#DCE4F0", dp(16).toFloat(), 2)
                setPadding(dp(22), dp(22), dp(22), dp(22))
                layoutParams = createCardLayoutParams(bottomMarginDp = 18)
            }
            emptyCard.addView(TextView(this).apply {
                text = "هنوز هیچ گردش حسابی (واریز یا برداشت) ثبت نشده است."
                textSize = 14f
                setTextColor(Color.parseColor("#64748B"))
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

            val txCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createOutlinedBg("#FFFFFF", "#DCE4F0", dp(16).toFloat(), 2)
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
                setTextColor(Color.parseColor("#1E293B"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            topRow.addView(TextView(this).apply {
                text = if (isDeposit) "+ ${formatNumber(amount)}" else "- ${formatNumber(amount)}"
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(if (isDeposit) "#10B981" else "#EF4444"))
            })

            txCard.addView(topRow)

            txCard.addView(TextView(this).apply {
                text = "بخش: $cat   |   تاریخ: $date"
                textSize = 13f
                setTextColor(Color.parseColor("#475569"))
                setPadding(0, dp(8), 0, dp(4))
            })

            if (txBalance >= 0) {
                txCard.addView(TextView(this).apply {
                    text = "مانده حساب پس از تراکنش: ${formatNumber(txBalance)}"
                    textSize = 12.5f
                    setTextColor(Color.parseColor("#1D63B8"))
                })
            }

            pageContentContainer.addView(txCard)
        }
    }

    // =========================================================================
    // صفحه ۴: ابزارها و تست پیامک بانک
    // =========================================================================
    private fun renderToolsAndSimulatorPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createOutlinedBg("#FFFFFF", "#DCE4F0", dp(16).toFloat(), 2)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 20)
        }

        infoCard.addView(TextView(this).apply {
            text = "⚙️ شبیه‌ساز پیامک و مدیریت دوره ۱۰ روزه"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1D63B8"))
        })

        infoCard.addView(TextView(this).apply {
            text = "\nاین برنامه به محض دریافت پیامک از بانک شما، مبلغ و مانده را می‌خواند. برای آزمایش فوری عملکرد برنامه با فرمت پیامک خودتان، روی دکمه‌های زیر بزنید:"
            textSize = 13.5f
            setTextColor(Color.parseColor("#475569"))
            setLineSpacing(8f, 1f)
        })
        pageContentContainer.addView(infoCard)

        val testWithdrawBtn = Button(this).apply {
            text = "🧪 شبیه‌سازی پیامک برداشت (۱,۵۱۱,۰۰۰)"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = createRoundedBg("#1D63B8", dp(14).toFloat())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = createCardLayoutParams(bottomMarginDp = 16)
            setOnClickListener {
                val sampleWithdrawSms = """
                    حساب ********
                    برداشت1,511,000
                    مانده712,744
                    05/08/21-10:50
                """.trimIndent()
                SmsParser.processBankSms(this@MainActivity, sampleWithdrawSms)
            }
        }
        pageContentContainer.addView(testWithdrawBtn)

        val testDepositBtn = Button(this).apply {
            text = "🧪 شبیه‌سازی پیامک واریز (۱,۲۵۰,۰۰۰)"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            background = createRoundedBg("#10B981", dp(14).toFloat())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = createCardLayoutParams(bottomMarginDp = 24)
            setOnClickListener {
                val sampleDepositSms = """
                    حساب ********
                    واریز1,250,000
                    مانده1,962,744
                    05/08/21-19:57
                """.trimIndent()
                SmsParser.processBankSms(this@MainActivity, sampleDepositSms)
            }
        }
        pageContentContainer.addView(testDepositBtn)

        val resetBtn = Button(this).apply {
            text = "🔄 شروع دوره ۱۰ روزه جدید (پاک کردن لیست فعلی)"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            background = createOutlinedBg("#FFFFFF", "#EF4444", dp(14).toFloat(), 2)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = createCardLayoutParams(bottomMarginDp = 20)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("شروع دوره ۱۰ روزه جدید")
                    .setMessage("آیا مطمئن هستید که می‌خواهید اطلاعات دوره فعلی را پاک کنید و ۱۰ روز جدید را آغاز کنید؟")
                    .setPositiveButton("بله، شروع شود") { _, _ ->
                        prefs.edit()
                            .putString(KEY_TRANSACTIONS, "[]")
                            .putLong(KEY_CYCLE_START, System.currentTimeMillis())
                            .apply()
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

    private fun createRoundedBg(hexColor: String, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor(hexColor))
        }
    }

    private fun createOutlinedBg(
        fillHex: String,
        borderHex: String,
        radius: Float,
        strokeWidth: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.parseColor(fillHex))
            setStroke(strokeWidth, Color.parseColor(borderHex))
        }
    }
}

// =========================================================================
// ویوی اختصاصی نمودار میله‌ای آبی‌رنگ (مشابه نمودار میله‌ای چرتکه)
// =========================================================================
class ChortkeBarChartView(
    context: Context,
    private val values: List<Float>,
    private val labels: List<String>
) : View(context) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D63B8")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569")
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

        // خطوط افقی راهنمای پس‌زمینه
        for (i in 0..3) {
            val y = 16f + (chartHeight / 3f) * i
            canvas.drawLine(16f, y, w - 16f, y, gridPaint)
        }

        if (values.isEmpty()) return
        val maxVal = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val count = values.size
        val slotWidth = (w - 32f) / count
        val barWidth = min(slotWidth * 0.38f, 44f)

        for (i in 0 until count) {
            val cx = 16f + slotWidth * i + slotWidth / 2f
            val ratio = if (values[i] > 0f) (values[i] / maxVal).coerceAtLeast(0.08f) else 0.04f
            val barTop = 16f + chartHeight * (1f - ratio)
            val barBottom = 16f + chartHeight

            barPaint.color = if (values[i] > 0f) Color.parseColor("#1D63B8") else Color.parseColor("#CBD5E1")
            rectF.set(cx - barWidth / 2f, barTop, cx + barWidth / 2f, barBottom)
            canvas.drawRoundRect(rectF, 8f, 8f, barPaint)

            val shortLabel = labels.getOrNull(i)?.take(10) ?: ""
            canvas.drawText(shortLabel, cx, h - 12f, textPaint)
        }
    }
}

// =========================================================================
// ویوی اختصاصی نمودار قوسی / نیم‌دایره رنگارنگ (مشابه نمودار چرتکه)
// =========================================================================
data class ArchSlice(val value: Float, val color: Int)

class ChortkeArchChartView(
    context: Context,
    private val slices: List<ArchSlice>
) : View(context) {

    private val archPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val emptyArchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#E2E8F0")
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

        // رسم قوس ۲۰0 درجه‌ای پس‌زمینه (مشابه قوس چرتکه از زاویه ۱۷۰ تا ۳۷۰)
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
// دریافت خودکار پیامک و تحلیلگر فرمت بانک شما
// =========================================================================
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val fullText = StringBuilder()
            for (msg in messages) {
                fullText.append(msg.messageBody)
            }
            SmsParser.processBankSms(context, fullText.toString())
        }
    }
}

object SmsParser {
    fun processBankSms(context: Context, rawText: String) {
        val text = normalizeDigits(rawText)

        val depositMatch = Regex("""واریز\s*:?\s*([\d,]+)""").find(text)
        val withdrawMatch = Regex("""برداشت\s*:?\s*([\d,]+)""").find(text)
        val balanceMatch = Regex("""مانده\s*:?\s*([\d,]+)""").find(text)
        val dateMatch = Regex("""(\d{2}/\d{2}/\d{2}-\d{2}:\d{2})""").find(text)

        if (depositMatch == null && withdrawMatch == null) return

        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(MainActivity.KEY_TRANSACTIONS, "[]"))

        val balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        val dateStr = dateMatch?.groupValues?.get(1) ?: "امروز"

        val editor = prefs.edit()
        if (balance != null) {
            editor.putLong(MainActivity.KEY_BALANCE, balance)
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

    private fun normalizeDigits(input: String): String {
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
