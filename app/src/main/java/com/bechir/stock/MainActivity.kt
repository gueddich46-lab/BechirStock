package com.bechir.stock

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 12, 28)
    private val panel = Color.rgb(38, 27, 60)
    private val accent = Color.rgb(173, 92, 245)
    private val data get() = getSharedPreferences("stock_data", MODE_PRIVATE)
    private lateinit var root: LinearLayout
    private val fmt = DecimalFormat("0.00")
    private fun txt(s: String, size: Float = 17f, color: Int = Color.WHITE): TextView =
        TextView(this).apply { text = s; textSize = size; setTextColor(color); gravity = Gravity.RIGHT; setPadding(14, 14, 14, 14) }
    private fun button(s: String, action: () -> Unit): Button =
        Button(this).apply { text = s; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent)); setOnClickListener { action() } }
    private fun field(hintText: String, number: Boolean = false): EditText =
        EditText(this).apply { hint = hintText; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE); setSingleLine(true); setPadding(16, 15, 16, 15); if(number) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
    private fun items(): JSONArray = try { JSONArray(data.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
    private fun save(items: JSONArray) { data.edit().putString("items", items.toString()).apply() }
    private fun amount(s: String): Double = s.trim().replace(",", ".").toDoubleOrNull() ?: 0.0
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); home() }
    private fun page(title: String) {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); fitsSystemWindows = true }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 60) }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(txt(title, 25f))
    }
    private fun menu() {
        root.addView(button("الرئيسية") { home() })
        root.addView(button("إضافة مشتريات") { addPurchase() })
        root.addView(button("المخزون") { inventory() })
        root.addView(button("المبيعات") { sales() })
    }
    private fun home() {
        page("BECHIR STOCK  |  بشير ستوك")
        root.addView(txt("إدارة تجارة الفريب الخاصة بي", 16f, Color.LTGRAY))
        val arr = items()
        var stock = 0; var sold = 0; var cost = 0.0; var revenue = 0.0; var profit = 0.0
        for (i in 0 until arr.length()) {
            val it = arr.getJSONObject(i)
            val quantity = it.optInt("quantity", 1); val soldQty = it.optInt("sold", 0)
            val unitCost = it.optDouble("cost", 0.0); val sale = it.optDouble("sale", 0.0)
            stock += quantity - soldQty; sold += soldQty; cost += (quantity - soldQty) * unitCost
            revenue += soldQty * sale; profit += soldQty * (sale - unitCost)
        }
        root.addView(txt("المخزون: $stock قطعة", 21f))
        root.addView(txt("القطع المباعة: $sold", 20f))
        root.addView(txt("قيمة المخزون بالتكلفة: ${fmt.format(cost)} د.ت", 19f))
        root.addView(txt("إجمالي المبيعات: ${fmt.format(revenue)} د.ت", 19f))
        root.addView(txt("الربح المحقق: ${fmt.format(profit)} د.ت", 21f, Color.rgb(103, 240, 157)))
        menu()
    }
    private fun addPurchase() {
        page("إضافة شراء جديد")
        val options = arrayOf("بالقطعة", "بالبالة / الكيلو")
        val mode = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options) }
        root.addView(mode)
        val name = field("اسم المنتج أو البالة")
        val category = field("التصنيف / الماركة")
        val quantity = field("عدد القطع", true)
        val cost = field("ثمن الشراء الإجمالي بالدينار", true)
        val expenses = field("مصاريف إضافية (نقل...) بالدينار", true)
        val sale = field("سعر بيع القطعة بالدينار", true)
        listOf(name, category, quantity, cost, expenses, sale).forEach { root.addView(it) }
        val preview = txt("التكلفة لكل قطعة: —", 18f, Color.rgb(103,240,157))
        root.addView(preview)
        root.addView(button("احسب تكلفة القطعة") {
            val qty = quantity.text.toString().toIntOrNull() ?: 0
            preview.text = if (qty > 0) "التكلفة لكل قطعة: ${fmt.format((amount(cost.text.toString()) + amount(expenses.text.toString())) / qty)} د.ت" else "أدخل عدد قطع صحيحًا"
        })
        root.addView(button("حفظ المشتريات") {
            val qty = quantity.text.toString().toIntOrNull() ?: 0
            val price = amount(cost.text.toString()); val extra = amount(expenses.text.toString())
            val salePrice = amount(sale.text.toString())
            if (name.text.isBlank() || qty < 1 || price < 0 || extra < 0 || salePrice < 0 || cost.text.isBlank()) {
                Toast.makeText(this, "تحقق من الاسم والعدد والأسعار", Toast.LENGTH_LONG).show()
            } else {
                val arr = items()
                arr.put(JSONObject().put("name", name.text.toString()).put("category", category.text.toString())
                    .put("quantity", qty).put("sold", 0).put("cost", (price + extra) / qty)
                    .put("sale", salePrice).put("mode", options[mode.selectedItemPosition]))
                save(arr); home()
            }
        })
        root.addView(button("رجوع") { home() })
    }
    private fun inventory() {
        page("المخزون")
        val arr = items()
        if (arr.length() == 0) root.addView(txt("لا توجد بضاعة مسجلة بعد."))
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val remaining = item.optInt("quantity") - item.optInt("sold")
            root.addView(txt("${item.optString("name")}  |  ${item.optString("category")}\nالمتبقي: $remaining / ${item.optInt("quantity")}\nتكلفة القطعة: ${fmt.format(item.optDouble("cost"))} د.ت\nسعر البيع: ${fmt.format(item.optDouble("sale"))} د.ت", 17f))
            if (remaining > 0) root.addView(button("تسجيل بيع قطعة: ${item.optString("name")}") { sell(i) })
            root.addView(View(this).apply { setBackgroundColor(panel); layoutParams = LinearLayout.LayoutParams(-1, 2) })
        }
        root.addView(button("رجوع") { home() })
    }
    private fun sell(index: Int) {
        val arr = items(); val item = arr.getJSONObject(index)
        val remaining = item.optInt("quantity") - item.optInt("sold")
        if (remaining <= 0) { inventory(); return }
        AlertDialog.Builder(this).setTitle("تأكيد البيع")
            .setMessage("تسجيل بيع قطعة واحدة من ${item.optString("name")} بسعر ${fmt.format(item.optDouble("sale"))} د.ت؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تأكيد") { _, _ ->
                item.put("sold", item.optInt("sold") + 1)
                save(arr); inventory()
            }.show()
    }
    private fun sales() {
        page("المبيعات والأرباح")
        val arr = items()
        var total = 0.0; var profit = 0.0; var count = 0
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i); val sold = item.optInt("sold")
            if (sold > 0) {
                count += sold
                total += sold * item.optDouble("sale")
                profit += sold * (item.optDouble("sale") - item.optDouble("cost"))
                root.addView(txt("${item.optString("name")}: $sold قطعة | ${fmt.format(sold * item.optDouble("sale"))} د.ت"))
            }
        }
        root.addView(txt("المبيعات: $count قطعة / ${fmt.format(total)} د.ت", 20f))
        root.addView(txt("الربح المحقق: ${fmt.format(profit)} د.ت", 21f, Color.rgb(103,240,157)))
        root.addView(button("رجوع") { home() })
    }
}
