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
import android.text.InputType
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

        const val PREFS_NAME = "PoolayeManPrefs"
        const val KEY_TRANSACTIONS = "transactions"
        const val KEY_BALANCE = "last_balance"
        const val KEY_BALANCE_DATE = "last_balance_date"
        const val KEY_ENTERTAINMENT_CAP = "entertainment_budget_cap"
        const val KEY_DEBTS = "student_debts"
        const val KEY_GOALS = "saving_goals"

        const val ENTERTAINMENT_CATEGORY = "تفریح و سرگرمی"
        const val DEFAULT_ENTERTAINMENT_CAP = 1500000L

        val EXPENSE_CATEGORIES = listOf(
            "خوراک",
            "تفریح و سرگرمی",
            "درمان و سلامت",
            "تحصیل و رشد فردی",
            "مسکن و رفت‌وآمد"
        )

        val EXPENSE_SUBCATEGORIES = mapOf(
            "خوراک" to listOf("سلف دانشگاه", "سوپرمارکت و خوراکی", "رستوران و فست‌فود", "کافه"),
            "تفریح و سرگرمی" to listOf("دورهمی با دوستان", "سینما و کنسرت", "سفر دانشجویی", "بازی و اشتراک‌ها"),
            "درمان و سلامت" to listOf("پزشک و دارو", "دندانپزشکی", "باشگاه و استخر", "محصولات بهداشتی"),
            "تحصیل و رشد فردی" to listOf("کتاب و جزوه", "پرینت و کپی", "دوره‌های آموزشی", "نرم‌افزار و اینترنت"),
            "مسکن و رفت‌وآمد" to listOf("خوابگاه یا اجاره", "مترو و اتوبوس", "اسنپ و تپسی", "شارژ و قبوض")
        )

        val BEHAVIOR_TAGS = listOf(
            "#ضروری",
            "#خرید_هیجانی",
            "#فرجه_امتحانات",
            "#عادی"
        )

        val INCOME_CATEGORIES = listOf(
            "کمک‌هزینه خانواده",
            "کار پاره‌وقت / پروژه",
            "وام دانشجویی",
            "هدیه و سایر"
        )

        val CHART_COLORS_EXPENSE = listOf("#FF5E8E", "#22D3EE", "#34D399", "#A855F7", "#FBBF24")
        val CHART_COLORS_INCOME = listOf("#34D399", "#38BDF8", "#818CF8", "#FACC15")
    }

    private lateinit var rootLayout: LinearLayout
    private lateinit var topSubTabBar: LinearLayout
    private lateinit var pageContentContainer: LinearLayout
    private lateinit var bottomNavBar: LinearLayout

    // 0 = سبک زندگی | 1 = سقف بودجه | 2 = دنگ‌ها | 3 = قلک
    private var currentTabIndex = 0
    private var showExpenseInBarChart = true
    private var isDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        window.statusBarColor = Color.parseColor("#090E1A")

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

        val topAppBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createGlassBg("#24FFFFFF", "#33FFFFFF", 0f, 1)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val appLogoBadge = TextView(this).apply {
            text = "💳"
            textSize = 18f
            gravity = Gravity.CENTER
            background = createGlassBg("#4D38BDF8", "#9938BDF8", dp(10).toFloat(), 2)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        }
        topAppBar.addView(appLogoBadge)

        val appTitle = TextView(this).apply {
            text = "پولای من"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topAppBar.addView(appTitle)

        val quickAddBadge = TextView(this).apply {
            text = "+ ثبت سریع"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = createGlassBg("#4D10B981", "#9934D399", dp(14).toFloat(), 2)
            setOnClickListener {
                showQuickEntryDialog()
            }
        }
        topAppBar.addView(quickAddBadge)
        rootLayout.addView(topAppBar)

        topSubTabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBg("#1AFFFFFF", "#26FFFFFF", 0f, 1)
        }
        rootLayout.addView(topSubTabBar)

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
            setPadding(dp(16), dp(16), dp(16), dp(30))
        }

        scrollView.addView(pageContentContainer)
        rootLayout.addView(scrollView)

        bottomNavBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBg("#29FFFFFF", "#4DFFFFFF", dp(20).toFloat(), 2)
            setPadding(dp(6), dp(8), dp(6), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(10), 0, dp(10), dp(10))
            }
        }
        rootLayout.addView(bottomNavBar)

        setContentView(rootLayout)

        initDefaultsIfNeeded()
        requestNeededPermissions()
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
        syncLatestBalanceFromSmsInbox(showToast = false)
        refreshUI()
        checkAndAskPendingTransactions()
    }

    override fun onPause() {
        super.onPause()
        onSmsReceivedUIUpdate = null
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

    private fun initDefaultsIfNeeded() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (!prefs.contains(KEY_ENTERTAINMENT_CAP)) {
            editor.putLong(KEY_ENTERTAINMENT_CAP, DEFAULT_ENTERTAINMENT_CAP)
        }

        if (!prefs.contains(KEY_GOALS)) {
            val defaultGoals = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("title", "خرید لپ‌تاپ / ارتقای سیستم")
                    put("target", 30000000L)
                    put("saved", 4500000L)
                })
                put(JSONObject().apply {
                    put("id", 2L)
                    put("title", "سفر تابستانی")
                    put("target", 5000000L)
                    put("saved", 1200000L)
                })
            }
            editor.putString(KEY_GOALS, defaultGoals.toString())
        }

        editor.apply()
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

    private fun showAskRealTransactionDialog(
        txId: Long,
        amount: Long,
        dateStr: String,
        type: String
    ) {
        isDialogShowing = true
        val isWithdraw = (type == "WITHDRAW")

        val scrollWrapper = ScrollView(this)
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = createGlassBg("#F5151C33", "#8038BDF8", dp(22).toFloat(), 3)
        }
        scrollWrapper.addView(dialogLayout)

        dialogLayout.addView(TextView(this).apply {
            text = if (isWithdraw) "✨ ثبت مخارج جدید" else "💰 ثبت درآمد جدید"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(if (isWithdraw) "#38BDF8" else "#34D399"))
        })

        dialogLayout.addView(TextView(this).apply {
            text = if (isWithdraw) {
                "\nمبلغ ${formatNumber(amount)} برداشت شد ($dateStr).\nچه چیزی با این مبلغ خریده‌اید؟"
            } else {
                "\nمبلغ ${formatNumber(amount)} واریز شد ($dateStr).\nتوضیح این درآمد چیست؟"
            }
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
        })

        val inputEdit = EditText(this).apply {
            hint = if (isWithdraw) "مثلاً: ناهار، قهوه، اسنپ..." else "مثلاً: کمک‌هزینه، پروژه..."
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = createGlassBg("#26FFFFFF", "#4DFFFFFF", dp(12).toFloat(), 2)
            layoutParams = createCardLayoutParams(bottomMarginDp = 12).apply { topMargin = dp(10) }
        }
        dialogLayout.addView(inputEdit)

        var selectedTag = BEHAVIOR_TAGS[0]

        if (isWithdraw) {
            dialogLayout.addView(TextView(this).apply {
                text = "🏷️ تگ رفتاری:"
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(4), 0, dp(6))
            })

            val tagRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = createCardLayoutParams(bottomMarginDp = 12)
            }

            val tagButtons = mutableListOf<TextView>()
            BEHAVIOR_TAGS.forEach { tag ->
                val tagBtn = TextView(this).apply {
                    text = tag
                    textSize = 11.5f
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setTextColor(Color.WHITE)
                    background = if (tag == selectedTag) {
                        createGlassBg("#5538BDF8", "#38BDF8", dp(10).toFloat(), 2)
                    } else {
                        createGlassBg("#1AFFFFFF", "#33FFFFFF", dp(10).toFloat(), 1)
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(3), 0, dp(3), 0)
                    }
                    setOnClickListener {
                        selectedTag = tag
                        tagButtons.forEach { b ->
                            b.background = if (b.text == selectedTag) {
                                createGlassBg("#5538BDF8", "#38BDF8", dp(10).toFloat(), 2)
                            } else {
                                createGlassBg("#1AFFFFFF", "#33FFFFFF", dp(10).toFloat(), 1)
                            }
                        }
                    }
                }
                tagButtons.add(tagBtn)
                tagRow.addView(tagBtn)
            }
            dialogLayout.addView(tagRow)
        }

        dialogLayout.addView(TextView(this).apply {
            text = if (isWithdraw) "📂 انتخاب دسته مخارج:" else "📂 انتخاب دسته درآمدها:"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, dp(4), 0, dp(6))
        })

        val dialog = AlertDialog.Builder(this)
            .setView(scrollWrapper)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val categoriesList = if (isWithdraw) EXPENSE_CATEGORIES else INCOME_CATEGORIES
        val btnColors = if (isWithdraw) CHART_COLORS_EXPENSE else CHART_COLORS_INCOME

        categoriesList.forEachIndexed { index, categoryName ->
            val subList = EXPENSE_SUBCATEGORIES[categoryName] ?: emptyList()
            val btn = Button(this).apply {
                text = if (isWithdraw && subList.isNotEmpty()) {
                    "$categoryName (${subList.joinToString("، ")})"
                } else {
                    categoryName
                }
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                background = createGlassBg(
                    addAlphaToHex(btnColors[index % btnColors.size], "44"),
                    btnColors[index % btnColors.size],
                    dp(12).toFloat(),
                    2
                )
                layoutParams = createCardLayoutParams(bottomMarginDp = 8)

                setOnClickListener {
                    val defaultTitle = if (isWithdraw) (subList.firstOrNull() ?: "خرید") else "درآمد"
                    val itemTitle = inputEdit.text.toString().trim().ifEmpty { defaultTitle }
                    val selectedSubcategory = subList.firstOrNull() ?: ""
                    updateTransactionDetails(txId, itemTitle, categoryName, selectedSubcategory, selectedTag)
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

    private fun showQuickEntryDialog() {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = createGlassBg("#F5151C33", "#8034D399", dp(22).toFloat(), 3)
        }

        dialogLayout.addView(TextView(this).apply {
            text = "⚡ ثبت سریع تراکنش"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#34D399"))
        })

        val amountInput = EditText(this).apply {
            hint = "۱. مبلغ (مثلاً: 85000)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = createGlassBg("#26FFFFFF", "#4DFFFFFF", dp(12).toFloat(), 2)
            layoutParams = createCardLayoutParams(bottomMarginDp = 10).apply { topMargin = dp(12) }
        }
        dialogLayout.addView(amountInput)

        val titleInput = EditText(this).apply {
            hint = "۲. توضیح کوتاه (مثلاً: قهوه، سلف، اسنپ...)"
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = createGlassBg("#26FFFFFF", "#4DFFFFFF", dp(12).toFloat(), 2)
            layoutParams = createCardLayoutParams(bottomMarginDp = 10)
        }
        dialogLayout.addView(titleInput)

        var selectedTag = "#ضروری"
        val tagRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = createCardLayoutParams(bottomMarginDp = 12)
        }
        val tagBtns = mutableListOf<TextView>()
        BEHAVIOR_TAGS.forEach { tag ->
            val tb = TextView(this).apply {
                text = tag
                textSize = 11.5f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(7), dp(6), dp(7))
                setTextColor(Color.WHITE)
                background = if (tag == selectedTag) {
                    createGlassBg("#5538BDF8", "#38BDF8", dp(10).toFloat(), 2)
                } else {
                    createGlassBg("#1AFFFFFF", "#33FFFFFF", dp(10).toFloat(), 1)
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    selectedTag = tag
                    tagBtns.forEach { b ->
                        b.background = if (b.text == selectedTag) {
                            createGlassBg("#5538BDF8", "#38BDF8", dp(10).toFloat(), 2)
                        } else {
                            createGlassBg("#1AFFFFFF", "#33FFFFFF", dp(10).toFloat(), 1)
                        }
                    }
                }
            }
            tagBtns.add(tb)
            tagRow.addView(tb)
        }
        dialogLayout.addView(tagRow)

        dialogLayout.addView(TextView(this).apply {
            text = "۳. انتخاب دسته:"
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 0, 0, dp(6))
        })

        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        EXPENSE_CATEGORIES.forEachIndexed { idx, cat ->
            val btn = Button(this).apply {
                text = cat
                setTextColor(Color.WHITE)
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                background = createGlassBg(
                    addAlphaToHex(CHART_COLORS_EXPENSE[idx], "44"),
                    CHART_COLORS_EXPENSE[idx],
                    dp(12).toFloat(),
                    2
                )
                layoutParams = createCardLayoutParams(bottomMarginDp = 8)
                setOnClickListener {
                    val amt = SmsParser.normalizeDigits(amountInput.text.toString()).toLongOrNull() ?: 0L
                    if (amt <= 0L) {
                        Toast.makeText(this@MainActivity, "لطفاً مبلغ را وارد کنید", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val title = titleInput.text.toString().trim().ifEmpty { cat }
                    addManualTransaction("WITHDRAW", amt, title, cat, selectedTag)
                    dialog.dismiss()
                    refreshUI()
                }
            }
            dialogLayout.addView(btn)
        }

        dialog.show()
    }

    private fun addManualTransaction(type: String, amount: Long, title: String, category: String, tag: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))
        val obj = JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("type", type)
            put("amount", amount)
            put("date", "ثبت دستی")
            put("balance", prefs.getLong(KEY_BALANCE, -1L))
            put("title", title)
            put("category", category)
            put("tag", tag)
            put("pending", false)
        }
        arr.put(obj)
        prefs.edit().putString(KEY_TRANSACTIONS, arr.toString()).apply()
    }

    private fun updateTransactionDetails(
        txId: Long,
        title: String,
        category: String,
        subcategory: String,
        tag: String
    ) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getLong("id") == txId) {
                obj.put("title", title)
                obj.put("category", category)
                obj.put("subcategory", subcategory)
                obj.put("tag", tag)
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
            0 -> renderLifestyleAnalyticsPage()
            1 -> renderBudgetingAndCategoriesPage()
            2 -> renderSplitBillAndDebtsPage()
            3 -> renderGoalsAndTurnoverPage()
        }
    }

    private fun renderTopSubTabs() {
        topSubTabBar.removeAllViews()
        val topTabs = listOf(
            "سبک زندگی" to 0,
            "سقف بودجه" to 1,
            "دنگ‌ها" to 2,
            "قلک" to 3
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
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(11), dp(4), dp(9))
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
            "📊 سبک زندگی",
            "🎯 سقف بودجه",
            "🤝 دنگ‌ها",
            "🐷 قلک"
        )

        navItems.forEachIndexed { index, label ->
            val isSelected = (index == currentTabIndex)
            val navBtn = TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(9), dp(4), dp(9))
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#94A3B8"))
                if (isSelected) {
                    background = createGlassBg("#4D38BDF8", "#9938BDF8", dp(12).toFloat(), 2)
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
    // تب ۱: سبک زندگی
    // =========================================================================
    private fun renderLifestyleAnalyticsPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))
        val lastBalance = prefs.getLong(KEY_BALANCE, 0L)
        val lastBalanceDate = prefs.getString(KEY_BALANCE_DATE, "در انتظار خواندن پیامک...") ?: ""

        var totalDeposit = 0L
        var totalWithdraw = 0L
        var needsTotal = 0L
        var wantsTotal = 0L
        var impulseCount = 0

        val expenseSums = mutableMapOf<String, Long>()
        val incomeSums = mutableMapOf<String, Long>()

        EXPENSE_CATEGORIES.forEach { expenseSums[it] = 0L }
        INCOME_CATEGORIES.forEach { incomeSums[it] = 0L }

        val totalTxCount = arr.length()

        for (i in 0 until totalTxCount) {
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
                val tag = obj.optString("tag", "#ضروری")
                expenseSums[cat] = (expenseSums[cat] ?: 0L) + amount

                if (tag == "#خرید_هیجانی") impulseCount++
                if (cat == ENTERTAINMENT_CATEGORY || tag == "#خرید_هیجانی") {
                    wantsTotal += amount
                } else {
                    needsTotal += amount
                }
            }
        }

        // ۱. کارت موجودی حساب بانکی شما
        val bankCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassBg("#26FFFFFF", "#6638BDF8", dp(22).toFloat(), 3)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 20)
        }

        bankCard.addView(TextView(this).apply {
            text = "🏦 موجودی حساب بانکی شما"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
        })

        bankCard.addView(TextView(this).apply {
            text = formatNumber(lastBalance)
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        })

        bankCard.addView(TextView(this).apply {
            text = "زمان آخرین پیامک مانده: $lastBalanceDate"
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })

        val refreshInboxBtn = Button(this).apply {
            text = "🔄 بروزرسانی مجدد موجودی از پیامک‌های گوشی"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createGlassBg("#3338BDF8", "#8038BDF8", dp(12).toFloat(), 2)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40))
            setOnClickListener {
                syncLatestBalanceFromSmsInbox(showToast = true)
                refreshUI()
            }
        }
        bankCard.addView(refreshInboxBtn)
        pageContentContainer.addView(bankCard)

        // ۲. تحلیل سبک زندگی (فقط بعد از ثبت حداقل ۱۰ تراکنش)
        if (totalTxCount >= 10) {
            val ruleCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createGlassBg("#22FFFFFF", "#66A855F7", dp(20).toFloat(), 2)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                layoutParams = createCardLayoutParams(bottomMarginDp = 20)
            }

            ruleCard.addView(TextView(this).apply {
                text = "⚖️ تحلیل سبک زندگی"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#C084FC"))
            })

            val needsPct = if (totalWithdraw > 0) ((needsTotal * 100f) / totalWithdraw).toInt() else 0
            val wantsPct = if (totalWithdraw > 0) ((wantsTotal * 100f) / totalWithdraw).toInt() else 0

            ruleCard.addView(TextView(this).apply {
                text = "\n• نیازهای ضروری (خوراک پایه، تحصیل، درمان، رفت‌وآمد): $needsPct٪ (${formatNumber(needsTotal)})\n" +
                        "• خواسته‌ها و تفریح (کافه، سرگرمی، خرید هیجانی): $wantsPct٪ (${formatNumber(wantsTotal)})\n" +
                        "• تعداد خریدهای با تگ #خرید_هیجانی: $impulseCount مورد"
                textSize = 13f
                setTextColor(Color.parseColor("#E2E8F0"))
                setLineSpacing(8f, 1f)
            })
            pageContentContainer.addView(ruleCard)
        }

        // ۳. کارت نمودار میله‌ای (مخارج و درآمدها)
        val barChartCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassBg("#22FFFFFF", "#4DFFFFFF", dp(22).toFloat(), 2)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 20)
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
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        totalInfoCol.addView(TextView(this).apply {
            text = if (showExpenseInBarChart) "مجموع مخارج" else "مجموع درآمدها"
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
            text = "مخارج"
            textSize = 12f
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
            textSize = 12f
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
                dp(160)
            ).apply {
                setMargins(0, dp(14), 0, 0)
            }
        }
        barChartCard.addView(barChartView)
        pageContentContainer.addView(barChartCard)

        // ۴. نمودار دایره‌ای با عنوان «هزینه‌ها»
        pageContentContainer.addView(
            buildGlassArchChartCard(
                badgeTitleText = "هزینه‌ها",
                borderHex = "#66FF5E8E",
                categories = EXPENSE_CATEGORIES,
                sumsMap = expenseSums,
                totalAmount = totalWithdraw,
                colorsHex = CHART_COLORS_EXPENSE,
                emptyHint = "هنوز هزینه‌ای ثبت نشده است."
            )
        )

        // ۵. نمودار دایره‌ای با عنوان «درآمدها»
        pageContentContainer.addView(
            buildGlassArchChartCard(
                badgeTitleText = "درآمدها",
                borderHex = "#6634D399",
                categories = INCOME_CATEGORIES,
                sumsMap = incomeSums,
                totalAmount = totalDeposit,
                colorsHex = CHART_COLORS_INCOME,
                emptyHint = "هنوز درآمدی ثبت نشده است."
            )
        )
    }

    private fun buildGlassArchChartCard(
        badgeTitleText: String,
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
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 20)
        }

        val headerBadge = TextView(this).apply {
            text = badgeTitleText
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(22), dp(7), dp(22), dp(7))
            background = createGlassBg("#4D38BDF8", "#9938BDF8", dp(12).toFloat(), 2)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(12))
            }
        }
        card.addView(headerBadge)

        val slices = categories.mapIndexed { idx, cat ->
            ArchSlice(
                value = (sumsMap[cat] ?: 0L).toFloat(),
                color = Color.parseColor(colorsHex[idx % colorsHex.size])
            )
        }

        val archChartView = GlassArchChartView(this, slices).apply {
            layoutParams = LinearLayout.LayoutParams(dp(230), dp(140))
        }
        card.addView(archChartView)

        card.addView(TextView(this).apply {
            text = if (totalAmount > 0) "جمع کل: ${formatNumber(totalAmount)}" else emptyHint
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        })

        categories.forEachIndexed { idx, cat ->
            val colorHex = colorsHex[idx % colorsHex.size]
            val amount = sumsMap[cat] ?: 0L
            val percent = if (totalAmount > 0) ((amount * 100f) / totalAmount).toInt() else 0

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createGlassBg("#1AFFFFFF", addAlphaToHex(colorHex, "55"), dp(12).toFloat(), 2)
                setPadding(dp(12), dp(9), dp(12), dp(9))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(4), 0, dp(4))
                }
            }

            row.addView(View(this).apply {
                background = createGlassBg(colorHex, "#FFFFFF", dp(6).toFloat(), 1)
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12))
            })

            row.addView(TextView(this).apply {
                text = "$cat ($percent٪)"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dp(10), 0, dp(10), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(TextView(this).apply {
                text = formatNumber(amount)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(colorHex))
            })

            card.addView(row)
        }

        return card
    }

    // =========================================================================
    // تب ۲: سقف بودجه (سقف بودجه فقط برای تفریح و سرگرمی)
    // =========================================================================
    private fun renderBudgetingAndCategoriesPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))
        val entertainmentCap = prefs.getLong(KEY_ENTERTAINMENT_CAP, DEFAULT_ENTERTAINMENT_CAP).coerceAtLeast(1L)

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
                val title = obj.optString("title", "خرید")
                val tag = obj.optString("tag", "#ضروری")
                val date = obj.optString("date", "")
                if (categorySums.containsKey(cat)) {
                    categorySums[cat] = (categorySums[cat] ?: 0L) + amount
                    categoryItems[cat]?.add("• $title : ${formatNumber(amount)}  [$tag]  ($date)")
                }
            }
        }

        val funSpent = categorySums[ENTERTAINMENT_CATEGORY] ?: 0L
        val usagePct = ((funSpent * 100f) / entertainmentCap).toInt()

        val statusColorHex = when {
            usagePct >= 100 -> "#EF4444"
            usagePct >= 80 -> "#F59E0B"
            else -> "#10B981"
        }

        val statusLabel = when {
            usagePct >= 100 -> "🚨 عبور از سقف بودجه تفریح ($usagePct٪)"
            usagePct >= 80 -> "⚠️ هشدار نزدیک شدن به سقف تفریح ($usagePct٪)"
            else -> "✅ مصرف تفریح در محدوده مجاز ($usagePct٪ از سقف)"
        }

        val funBudgetCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassBg("#28FFFFFF", addAlphaToHex(statusColorHex, "99"), dp(22).toFloat(), 3)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 22)
        }

        val funTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        funTopRow.addView(TextView(this).apply {
            text = "🎯 سقف بودجه تفریح"
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val editCapBtn = TextView(this).apply {
            text = "✏️ تغییر سقف: ${formatNumber(entertainmentCap)}"
            textSize = 12f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = createGlassBg("#2638BDF8", "#6638BDF8", dp(10).toFloat(), 1)
            setOnClickListener {
                showEditEntertainmentCapDialog(entertainmentCap)
            }
        }
        funTopRow.addView(editCapBtn)
        funBudgetCard.addView(funTopRow)

        funBudgetCard.addView(TextView(this).apply {
            text = "هزینه تفریح شما: ${formatNumber(funSpent)}  |  $statusLabel"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(statusColorHex))
            setPadding(0, dp(10), 0, dp(8))
        })

        val barTrack = FrameLayout(this).apply {
            background = createGlassBg("#26FFFFFF", "#33FFFFFF", dp(8).toFloat(), 1)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12))
        }
        val fillRatio = (usagePct / 100f).coerceIn(0.03f, 1f)
        val barFill = View(this).apply {
            background = createGlassBg(statusColorHex, statusColorHex, dp(8).toFloat(), 1)
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.78f * fillRatio).toInt(),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        barTrack.addView(barFill)
        funBudgetCard.addView(barTrack)

        val funItems = categoryItems[ENTERTAINMENT_CATEGORY] ?: emptyList()
        funBudgetCard.addView(TextView(this).apply {
            text = if (funItems.isEmpty()) "\nموردی در بخش تفریح ثبت نشده است." else "\n" + funItems.joinToString("\n")
            textSize = 13f
            setTextColor(Color.parseColor("#E2E8F0"))
            setLineSpacing(7f, 1f)
        })

        pageContentContainer.addView(funBudgetCard)

        EXPENSE_CATEGORIES.forEachIndexed { idx, cat ->
            if (cat == ENTERTAINMENT_CATEGORY) return@forEachIndexed

            val spent = categorySums[cat] ?: 0L
            val accentHex = CHART_COLORS_EXPENSE[idx % CHART_COLORS_EXPENSE.size]

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createGlassBg("#22FFFFFF", addAlphaToHex(accentHex, "66"), dp(20).toFloat(), 2)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                layoutParams = createCardLayoutParams(bottomMarginDp = 16)
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            topRow.addView(TextView(this).apply {
                text = cat
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            topRow.addView(TextView(this).apply {
                text = formatNumber(spent)
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(accentHex))
            })

            card.addView(topRow)

            val items = categoryItems[cat] ?: emptyList()
            val itemsText = if (items.isEmpty()) "موردی ثبت نشده است." else items.joinToString("\n")

            card.addView(TextView(this).apply {
                text = "\n$itemsText"
                textSize = 13f
                setTextColor(Color.parseColor("#CBD5E1"))
                setLineSpacing(7f, 1f)
            })

            pageContentContainer.addView(card)
        }
    }

    private fun showEditEntertainmentCapDialog(currentCap: Long) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentCap.toString())
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        AlertDialog.Builder(this)
            .setTitle("تعیین سقف بودجه تفریح")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val newCap = SmsParser.normalizeDigits(input.text.toString()).toLongOrNull() ?: currentCap
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_ENTERTAINMENT_CAP, newCap).apply()
                refreshUI()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // =========================================================================
    // تب ۳: دنگ‌ها
    // =========================================================================
    private fun renderSplitBillAndDebtsPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val dongCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassBg("#26FFFFFF", "#6622D3EE", dp(20).toFloat(), 3)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 22)
        }

        dongCard.addView(TextView(this).apply {
            text = "🍕 ماشین‌حساب دُنگ (ثبت فقط سهم شما)"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#22D3EE"))
        })

        val billInput = EditText(this).apply {
            hint = "کل مبلغ فاکتور (مثلاً: 600000)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = createGlassBg("#1AFFFFFF", "#4DFFFFFF", dp(12).toFloat(), 1)
            layoutParams = createCardLayoutParams(bottomMarginDp = 10).apply { topMargin = dp(10) }
        }
        dongCard.addView(billInput)

        val countInput = EditText(this).apply {
            hint = "تعداد نفرات (مثلاً: 3)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = createGlassBg("#1AFFFFFF", "#4DFFFFFF", dp(12).toFloat(), 1)
            layoutParams = createCardLayoutParams(bottomMarginDp = 10)
        }
        dongCard.addView(countInput)

        val titleAndFriendsInput = EditText(this).apply {
            hint = "توضیح و نام دوستان (مثلاً: پیتزا با علی و سارا)"
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = createGlassBg("#1AFFFFFF", "#4DFFFFFF", dp(12).toFloat(), 1)
            layoutParams = createCardLayoutParams(bottomMarginDp = 12)
        }
        dongCard.addView(titleAndFriendsInput)

        val calcDongBtn = Button(this).apply {
            text = "➗ محاسبه دُنگ و ثبت سهم"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = createGlassBg("#4D06B6D4", "#9922D3EE", dp(14).toFloat(), 2)
            setOnClickListener {
                val totalBill = SmsParser.normalizeDigits(billInput.text.toString()).toLongOrNull() ?: 0L
                val people = SmsParser.normalizeDigits(countInput.text.toString()).toIntOrNull() ?: 1
                if (totalBill <= 0L || people <= 1) {
                    Toast.makeText(this@MainActivity, "مبلغ کل و تعداد نفرات (بیشتر از ۱) را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val myShare = totalBill / people
                val friendsShare = totalBill - myShare
                val desc = titleAndFriendsInput.text.toString().trim().ifEmpty { "خرج مشترک" }

                addManualTransaction("WITHDRAW", myShare, "سهم من از $desc", ENTERTAINMENT_CATEGORY, "#عادی")

                val debtsArr = JSONArray(prefs.getString(KEY_DEBTS, "[]"))
                debtsArr.put(JSONObject().apply {
                    put("id", System.currentTimeMillis())
                    put("person", desc)
                    put("amount", friendsShare)
                    put("isReceivable", true)
                })
                prefs.edit().putString(KEY_DEBTS, debtsArr.toString()).apply()

                Toast.makeText(
                    this@MainActivity,
                    "✅ سهم شما (${formatNumber(myShare)}) در مخارج و سهم دوستان (${formatNumber(friendsShare)}) در دنگ‌ها ثبت شد!",
                    Toast.LENGTH_LONG
                ).show()
                refreshUI()
            }
        }
        dongCard.addView(calcDongBtn)
        pageContentContainer.addView(dongCard)

        val debtCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassBg("#22FFFFFF", "#66FBBF24", dp(20).toFloat(), 2)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 20)
        }

        val debtHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = createCardLayoutParams(bottomMarginDp = 12)
        }

        debtHeaderRow.addView(TextView(this).apply {
            text = "📓 دفترچه طلب و بدهی دوستانه"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FBBF24"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val addDebtBtn = TextView(this).apply {
            text = "+ ثبت طلب/بدهی"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = createGlassBg("#4DF59E0B", "#99FBBF24", dp(10).toFloat(), 1)
            setOnClickListener {
                showAddDebtDialog()
            }
        }
        debtHeaderRow.addView(addDebtBtn)
        debtCard.addView(debtHeaderRow)

        val debtsArr = JSONArray(prefs.getString(KEY_DEBTS, "[]"))
        if (debtsArr.length() == 0) {
            debtCard.addView(TextView(this).apply {
                text = "هیچ طلب یا بدهی ثبت نشده است."
                textSize = 13f
                setTextColor(Color.parseColor("#94A3B8"))
            })
        } else {
            for (i in 0 until debtsArr.length()) {
                val obj = debtsArr.getJSONObject(i)
                val id = obj.getLong("id")
                val person = obj.getString("person")
                val amount = obj.getLong("amount")
                val isReceivable = obj.getBoolean("isReceivable")

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = createGlassBg("#1AFFFFFF", "#33FFFFFF", dp(12).toFloat(), 1)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = createCardLayoutParams(bottomMarginDp = 8)
                }

                row.addView(TextView(this).apply {
                    text = if (isReceivable) {
                        "🟢 طلبکارم از «$person» : ${formatNumber(amount)}"
                    } else {
                        "🔴 بدهکارم به «$person» : ${formatNumber(amount)}"
                    }
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor(if (isReceivable) "#34D399" else "#FCA5A5"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                val settleBtn = TextView(this).apply {
                    text = "تسویه شد ✓"
                    textSize = 11.5f
                    setTextColor(Color.WHITE)
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    background = createGlassBg("#3310B981", "#8034D399", dp(8).toFloat(), 1)
                    setOnClickListener {
                        removeDebtById(id)
                        refreshUI()
                    }
                }
                row.addView(settleBtn)
                debtCard.addView(row)
            }
        }

        pageContentContainer.addView(debtCard)
    }

    private fun showAddDebtDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val personInput = EditText(this).apply {
            hint = "نام دوست (مثلاً: سارا، علی...)"
        }
        val amountInput = EditText(this).apply {
            hint = "مبلغ (مثلاً: 200000)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(personInput)
        layout.addView(amountInput)

        AlertDialog.Builder(this)
            .setTitle("ثبت طلب یا بدهی")
            .setView(layout)
            .setPositiveButton("طلبکارم (+)") { _, _ ->
                saveDebtItem(personInput.text.toString(), amountInput.text.toString(), true)
            }
            .setNegativeButton("بدهکارم (-)") { _, _ ->
                saveDebtItem(personInput.text.toString(), amountInput.text.toString(), false)
            }
            .show()
    }

    private fun saveDebtItem(personRaw: String, amountRaw: String, isReceivable: Boolean) {
        val amt = SmsParser.normalizeDigits(amountRaw).toLongOrNull() ?: 0L
        if (amt <= 0L) return
        val person = personRaw.trim().ifEmpty { "دوست" }
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_DEBTS, "[]"))
        arr.put(JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("person", person)
            put("amount", amt)
            put("isReceivable", isReceivable)
        })
        prefs.edit().putString(KEY_DEBTS, arr.toString()).apply()
        refreshUI()
    }

    private fun removeDebtById(targetId: Long) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_DEBTS, "[]"))
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getLong("id") != targetId) newArr.put(obj)
        }
        prefs.edit().putString(KEY_DEBTS, newArr.toString()).apply()
    }

    // =========================================================================
    // تب ۴: قلک
    // =========================================================================
    private fun renderGoalsAndTurnoverPage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val goalsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassBg("#24FFFFFF", "#6634D399", dp(20).toFloat(), 3)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = createCardLayoutParams(bottomMarginDp = 22)
        }

        val goalHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = createCardLayoutParams(bottomMarginDp = 12)
        }

        goalHeader.addView(TextView(this).apply {
            text = "🐷 قلک هدف‌گذاری"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#34D399"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val addGoalBtn = TextView(this).apply {
            text = "+ هدف جدید"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = createGlassBg("#4D10B981", "#9934D399", dp(10).toFloat(), 1)
            setOnClickListener {
                showAddGoalDialog()
            }
        }
        goalHeader.addView(addGoalBtn)
        goalsCard.addView(goalHeader)

        val goalsArr = JSONArray(prefs.getString(KEY_GOALS, "[]"))
        for (i in 0 until goalsArr.length()) {
            val g = goalsArr.getJSONObject(i)
            val id = g.getLong("id")
            val title = g.getString("title")
            val target = g.getLong("target").coerceAtLeast(1L)
            val saved = g.getLong("saved")
            val pct = ((saved * 100f) / target).toInt().coerceAtMost(100)

            val itemBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createGlassBg("#1AFFFFFF", "#33FFFFFF", dp(14).toFloat(), 1)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = createCardLayoutParams(bottomMarginDp = 10)
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            topRow.addView(TextView(this).apply {
                text = "$title ($pct٪)"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            val depositGoalBtn = TextView(this).apply {
                text = "+ واریز به قلک"
                textSize = 11.5f
                setTextColor(Color.parseColor("#34D399"))
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = createGlassBg("#2610B981", "#6634D399", dp(8).toFloat(), 1)
                setOnClickListener {
                    showDepositToGoalDialog(id, title)
                }
            }
            topRow.addView(depositGoalBtn)
            itemBox.addView(topRow)

            itemBox.addView(TextView(this).apply {
                text = "پس‌انداز شده: ${formatNumber(saved)} از ${formatNumber(target)}"
                textSize = 12f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, dp(6), 0, dp(6))
            })

            val barTrack = FrameLayout(this).apply {
                background = createGlassBg("#26FFFFFF", "#33FFFFFF", dp(6).toFloat(), 1)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10))
            }
            val ratio = (pct / 100f).coerceIn(0.03f, 1f)
            barTrack.addView(View(this).apply {
                background = createGlassBg("#34D399", "#34D399", dp(6).toFloat(), 1)
                layoutParams = FrameLayout.LayoutParams(
                    (resources.displayMetrics.widthPixels * 0.75f * ratio).toInt(),
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            })
            itemBox.addView(barTrack)
            goalsCard.addView(itemBox)
        }
        pageContentContainer.addView(goalsCard)

        pageContentContainer.addView(TextView(this).apply {
            text = "🔄 گردش حساب اخیر:"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, dp(4), dp(10))
        })

        val arr = JSONArray(prefs.getString(KEY_TRANSACTIONS, "[]"))
        if (arr.length() == 0) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createGlassBg("#22FFFFFF", "#4DFFFFFF", dp(18).toFloat(), 2)
                setPadding(dp(20), dp(20), dp(20), dp(20))
            }
            emptyCard.addView(TextView(this).apply {
                text = "هنوز تراکنشی ثبت نشده است."
                textSize = 13.5f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
            })
            pageContentContainer.addView(emptyCard)
        } else {
            for (i in arr.length() - 1 downTo 0) {
                val obj = arr.getJSONObject(i)
                val type = obj.getString("type")
                val amount = obj.getLong("amount")
                val date = obj.optString("date", "")
                val title = obj.optString("title", "تراکنش")
                val cat = obj.optString("category", "")
                val tag = obj.optString("tag", "")
                val txBalance = obj.optLong("balance", -1L)
                val isDeposit = (type == "DEPOSIT")

                val txCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = createGlassBg(
                        "#22FFFFFF",
                        if (isDeposit) "#6634D399" else "#66FF5E8E",
                        dp(16).toFloat(),
                        2
                    )
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    layoutParams = createCardLayoutParams(bottomMarginDp = 12)
                }

                txCard.addView(TextView(this).apply {
                    text = if (isDeposit) "+ ${formatNumber(amount)} ($title)" else "- ${formatNumber(amount)} ($title)"
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor(if (isDeposit) "#34D399" else "#FF5E8E"))
                })

                val balanceStr = if (txBalance >= 0) " | مانده: ${formatNumber(txBalance)}" else ""
                txCard.addView(TextView(this).apply {
                    text = "دسته: $cat ${if (tag.isNotEmpty()) "| $tag" else ""} | $date$balanceStr"
                    textSize = 12f
                    setTextColor(Color.parseColor("#CBD5E1"))
                    setPadding(0, dp(6), 0, 0)
                })

                pageContentContainer.addView(txCard)
            }
        }
    }

    private fun showAddGoalDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val titleInput = EditText(this).apply { hint = "عنوان هدف (مثلاً: خرید تبلت، دوره زبان)" }
        val targetInput = EditText(this).apply {
            hint = "مبلغ هدف (مثلاً: 10000000)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(titleInput)
        layout.addView(targetInput)

        AlertDialog.Builder(this)
            .setTitle("تعریف هدف جدید در قلک")
            .setView(layout)
            .setPositiveButton("ایجاد هدف") { _, _ ->
                val target = SmsParser.normalizeDigits(targetInput.text.toString()).toLongOrNull() ?: 0L
                if (target > 0L) {
                    val title = titleInput.text.toString().trim().ifEmpty { "هدف جدید" }
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val arr = JSONArray(prefs.getString(KEY_GOALS, "[]"))
                    arr.put(JSONObject().apply {
                        put("id", System.currentTimeMillis())
                        put("title", title)
                        put("target", target)
                        put("saved", 0L)
                    })
                    prefs.edit().putString(KEY_GOALS, arr.toString()).apply()
                    refreshUI()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showDepositToGoalDialog(goalId: Long, goalTitle: String) {
        val input = EditText(this).apply {
            hint = "مبلغ افزوده شده به پس‌انداز"
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        AlertDialog.Builder(this)
            .setTitle("افزودن پس‌انداز به «$goalTitle»")
            .setView(input)
            .setPositiveButton("افزودن") { _, _ ->
                val addAmt = SmsParser.normalizeDigits(input.text.toString()).toLongOrNull() ?: 0L
                if (addAmt > 0L) {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val arr = JSONArray(prefs.getString(KEY_GOALS, "[]"))
                    for (i in 0 until arr.length()) {
                        val g = arr.getJSONObject(i)
                        if (g.getLong("id") == goalId) {
                            g.put("saved", g.getLong("saved") + addAmt)
                            break
                        }
                    }
                    prefs.edit().putString(KEY_GOALS, arr.toString()).apply()
                    refreshUI()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
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
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val bottomSpace = 46f
        val chartHeight = h - bottomSpace - 16f

        for (i in 0..3) {
            val y = 16f + (chartHeight / 3f) * i
            canvas.drawLine(16f, y, w - 16f, y, gridPaint)
        }

        if (values.isEmpty()) return
        val maxVal = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val count = values.size
        val slotWidth = (w - 32f) / count
        val barWidth = min(slotWidth * 0.38f, 42f)

        val activeBarColor = if (isExpenseMode) Color.parseColor("#38BDF8") else Color.parseColor("#34D399")

        for (i in 0 until count) {
            val cx = 16f + slotWidth * i + slotWidth / 2f
            val ratio = if (values[i] > 0f) (values[i] / maxVal).coerceAtLeast(0.08f) else 0.05f
            val barTop = 16f + chartHeight * (1f - ratio)
            val barBottom = 16f + chartHeight

            barPaint.color = if (values[i] > 0f) activeBarColor else Color.parseColor("#33FFFFFF")
            rectF.set(cx - barWidth / 2f, barTop, cx + barWidth / 2f, barBottom)
            canvas.drawRoundRect(rectF, 10f, 10f, barPaint)

            val shortLabel = labels.getOrNull(i)?.take(8) ?: ""
            canvas.drawText(shortLabel, cx, h - 12f, textPaint)
        }
    }
}

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

        val dateStr = dateMatch?.groupValues?.get(1) ?: "امروز"

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
                    put("title", "درآمد جدید")
                    put("category", MainActivity.INCOME_CATEGORIES[0])
                    put("tag", "#درآمد")
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
                    put("tag", "#ضروری")
                    put("pending", true)
                }
                arr.put(obj)
                editor.putString(MainActivity.KEY_TRANSACTIONS, arr.toString()).apply()
                sendNotification(
                    context,
                    "برداشت بانکی: چه چیزی خریدید؟",
                    "مبلغ ${withdrawMatch.groupValues[1]} برداشت شد. ضربه بزنید و دسته و تگ را مشخص کنید."
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
        val channelId = "poolaye_man_channel"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "اعلان تراکنش‌های پولای من", NotificationManager.IMPORTANCE_HIGH)
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
