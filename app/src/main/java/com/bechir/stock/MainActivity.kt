package com.bechir.stock

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
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
    private var pendingPhoto: Uri? = null
    private var processedPhoto: String? = null
    private var photoPreview: ImageView? = null
    private val soft = Color.rgb(31, 25, 49)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun hanger(): View = object : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val scale = dp(1).toFloat()
            val cx = width / 2f
            val top = (height - dp(58)) / 2f
            // Symmetric hanger: round hook, short centered neck, and smooth shoulders.
            val hook = Path().apply {
                moveTo(cx, top + 24f * scale)
                lineTo(cx, top + 19f * scale)
                cubicTo(cx + 12f*scale, top + 17f*scale, cx + 11f*scale, top + 4f*scale, cx, top + 4f*scale)
                cubicTo(cx - 12f*scale, top + 4f*scale, cx - 12f*scale, top + 17f*scale, cx - 6f*scale, top + 18f*scale)
            }
            canvas.drawPath(hook, paint)
            val body = Path().apply {
                moveTo(cx, top + 24f*scale)
                cubicTo(cx - 5f*scale, top + 27f*scale, cx - 21f*scale, top + 37f*scale, cx - 33f*scale, top + 44f*scale)
                cubicTo(cx - 40f*scale, top + 49f*scale, cx - 37f*scale, top + 54f*scale, cx - 29f*scale, top + 54f*scale)
                lineTo(cx + 29f*scale, top + 54f*scale)
                cubicTo(cx + 37f*scale, top + 54f*scale, cx + 40f*scale, top + 49f*scale, cx + 33f*scale, top + 44f*scale)
                cubicTo(cx + 21f*scale, top + 37f*scale, cx + 5f*scale, top + 27f*scale, cx, top + 24f*scale)
            }
            canvas.drawPath(body, paint)
        }
    }
    private fun shape(color: Int, radius: Int = 18): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun card(title: String, value: String, color: Int = Color.WHITE): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(soft)
            minimumHeight = dp(103)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(txt(title, 13f, Color.rgb(197, 186, 215)))
            addView(txt(value, 20f, color))
        }
    private fun rowCards(a: Pair<String,String>, b: Pair<String,String>, highlight: Boolean = false) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = card(a.first,a.second,if(highlight) Color.rgb(103,240,157) else Color.WHITE)
        val right = card(b.first,b.second)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        row.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(9) })
    }
    private val fmt = DecimalFormat("0.00")
    private fun txt(s: String, size: Float = 17f, color: Int = Color.WHITE): TextView =
        TextView(this).apply { text = s; textSize = size; setTextColor(color); gravity = Gravity.RIGHT; setPadding(14, 14, 14, 14) }
    private fun button(s: String, action: () -> Unit): Button =
        Button(this).apply {
            text=s; textSize=15f; isAllCaps=false; setTextColor(Color.WHITE)
            background=shape(accent, 15)
            setPadding(dp(12),dp(8),dp(12),dp(8))
            setOnClickListener { action() }
        }
    private fun field(hintText: String, number: Boolean = false): EditText =
        EditText(this).apply { hint = hintText; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE); setSingleLine(true); setPadding(16, 15, 16, 15); if(number) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
    private fun items(): JSONArray = try { JSONArray(data.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
    private fun save(items: JSONArray) { data.edit().putString("items", items.toString()).apply() }
    private fun amount(s: String): Double = s.trim().replace(",", ".").toDoubleOrNull() ?: 0.0
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); home() }
    private fun page(title: String) {
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val frame = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(30))
        }
        scroll.addView(root)
        frame.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape(Color.rgb(24, 19, 39), 20)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        listOf("⌂\nالرئيسية", "▣\nالمخزون", "▤\nالمبيعات", "▥\nالتقارير").forEachIndexed { index, text ->
            val item = TextView(this).apply {
                this.text=text; textSize=12f; gravity=Gravity.CENTER; setTextColor(if ((index==0 && title.contains("STOCK")) || title.contains(text.substringAfter("\n"))) accent else Color.WHITE)
                setPadding(0,dp(8),0,dp(8))
                setOnClickListener { when(index) { 0->home(); 1->inventory(); 2->sales(); else->reports() } }
            }
            nav.addView(item,LinearLayout.LayoutParams(0,dp(59),1f))
        }
        frame.addView(nav)
        setContentView(frame)
        if (!title.contains("STOCK")) root.addView(txt(title, 23f))
    }
    private fun menu() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val purchase = button("🛒  شراء جديد") { addPurchase() }
        val sell = button("＋  بيع قطعة") { sellScreen() }
        row.addView(purchase, LinearLayout.LayoutParams(0,dp(55),1f).apply { marginEnd=dp(8) })
        row.addView(sell,LinearLayout.LayoutParams(0,dp(55),1f))
        root.addView(row)
    }
    private fun home() {
        page("BECHIR STOCK")
        root.addView(hanger(), LinearLayout.LayoutParams(-1,dp(67)))
        root.addView(txt("BECHIR STOCK", 23f).apply { gravity=Gravity.CENTER; setPadding(0,0,0,0) })
        root.addView(txt("بشير ستوك", 19f).apply { gravity=Gravity.CENTER; setPadding(0,0,0,0) })
        root.addView(txt("إدارة تجارة الفريب الخاصة بي", 14f,Color.rgb(197,186,215)).apply { gravity=Gravity.CENTER })
        val arr = items()
        var stock = 0; var sold = 0; var cost = 0.0; var revenue = 0.0; var profit = 0.0
        for (i in 0 until arr.length()) {
            val it = arr.getJSONObject(i)
            val quantity = it.optInt("quantity", 1); val soldQty = it.optInt("sold", 0)
            val unitCost = it.optDouble("cost", 0.0); val sale = it.optDouble("sale", 0.0)
            stock += quantity - soldQty; sold += soldQty; cost += (quantity - soldQty) * unitCost
            revenue += soldQty * sale; profit += soldQty * (sale - unitCost)
        }
        root.addView(txt("ملخص تجارتك", 16f, accent))
        rowCards("الربح المحقق" to "${fmt.format(profit)} د.ت", "المبيعات" to "$sold قطعة",true)
        rowCards("المخزون" to "$stock قطعة", "قيمة المخزون" to "${fmt.format(cost)} د.ت")
        rowCards("قيمة المشتريات" to "${fmt.format(cost + (0.0))} د.ت", "إجمالي المبيعات" to "${fmt.format(revenue)} د.ت")
        menu()
        root.addView(txt("جميع بياناتك محفوظة محليًا على هذا الهاتف.",13f,Color.LTGRAY))
    }
    private fun addPurchase() {
        page("إضافة شراء جديد")
        pendingPhoto = null
        processedPhoto = null
        photoPreview = null
        val options = arrayOf("بالقطعة", "بالبالة / الكيلو")
        val mode = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options) }
        root.addView(mode)
        val name = field("اسم المنتج أو البالة")
        val category = field("التصنيف / الماركة")
        val size = field("القياس (مثل 42 أو XL)")
        val quantity = field("عدد القطع", true)
        val cost = field("ثمن الشراء الإجمالي بالدينار", true)
        val expenses = field("مصاريف إضافية (نقل...) بالدينار", true)
        val sale = field("سعر بيع القطعة بالدينار", true)
        listOf(name, category, size, quantity, cost, expenses, sale).forEach { root.addView(it) }
        root.addView(button("📷  اختيار صورة المنتج") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, 101)
        })
        photoPreview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        root.addView(photoPreview, LinearLayout.LayoutParams(-1, dp(170)))
        root.addView(button("✨ إزالة الخلفية وجعلها بيضاء (تجريبي)") { removeBackground() })
        root.addView(button("↩ استعادة الصورة الأصلية") {
            processedPhoto = null
            pendingPhoto?.let { photoPreview?.setImageURI(it) }
        })
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
                    .put("sale", salePrice).put("size", size.text.toString().trim()).put("mode", options[mode.selectedItemPosition])
                    .put("photo", processedPhoto ?: pendingPhoto?.toString() ?: ""))
                save(arr); home()
            }
        })
        root.addView(button("رجوع") { home() })
    }
    private fun inventory() {
        page("المخزون")
        root.addView(button("＋  إضافة قطعة جديدة") { addPurchase() })
        val arr = items()
        if (arr.length() == 0) root.addView(txt("لا توجد بضاعة مسجلة بعد. أضف أول قطعة لعرضها هنا."))
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val remaining = item.optInt("quantity") - item.optInt("sold")
            val product = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = shape(soft, 18)
                setPadding(dp(12),dp(12),dp(12),dp(12))
            }
            val photo = item.optString("photo")
            if (photo.isNotBlank()) {
                val image = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = shape(Color.WHITE,12)
                    try { setImageURI(Uri.parse(photo)) } catch (_: Exception) { visibility=View.GONE }
                }
                product.addView(image, LinearLayout.LayoutParams(-1,dp(180)))
            }
            product.addView(txt(item.optString("name"),20f))
            product.addView(txt(item.optString("category")+"  •  "+if(remaining>0) "متوفر" else "نفدت الكمية",14f,accent))
            if (item.optString("size").isNotBlank()) product.addView(txt("القياس: "+item.optString("size"),16f))
            product.addView(txt("المتبقي: $remaining من ${item.optInt("quantity")}  |  التكلفة: ${fmt.format(item.optDouble("cost"))} د.ت",14f,Color.LTGRAY))
            product.addView(txt("سعر البيع: ${fmt.format(item.optDouble("sale"))} د.ت",17f,Color.rgb(103,240,157)))
            if (remaining > 0) product.addView(button("تسجيل بيع قطعة") { sell(i) })
            root.addView(product, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        }
    }

    private fun removeBackground() {
        val source = pendingPhoto
        if (source == null) {
            Toast.makeText(this,"اختر صورة أولًا",Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this,"جارٍ عزل المنتج، قد يلزم تنزيل نموذج عند أول استعمال",Toast.LENGTH_LONG).show()
        val options = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        val segmenter = SubjectSegmentation.getClient(options)
        try {
            val original = contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalStateException("تعذر فتح الصورة")
            val maxDimension = maxOf(original.width,original.height)
            val factor = minOf(1.0, 1280.0 / maxDimension)
            val scaled = if(factor < 1.0) Bitmap.createScaledBitmap(original,(original.width*factor).toInt(),(original.height*factor).toInt(),true) else original
            segmenter.process(InputImage.fromBitmap(scaled,0))
                .addOnSuccessListener { result ->
                    try {
                        val foreground = result.foregroundBitmap ?: throw IllegalStateException("لم يُعثر على المنتج")
                        val output = Bitmap.createBitmap(foreground.width,foreground.height,Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(output)
                        canvas.drawColor(Color.WHITE)
                        canvas.drawBitmap(foreground,0f,0f,null)
                        val file = java.io.File(filesDir,"stock_product_"+System.currentTimeMillis()+".png")
                        file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG,100,it) }
                        processedPhoto = Uri.fromFile(file).toString()
                        photoPreview?.apply { setImageBitmap(output); visibility=View.VISIBLE }
                        Toast.makeText(this,"تم تجهيز المعاينة. احفظ المشتريات لاعتماد الصورة.",Toast.LENGTH_LONG).show()
                    } catch(e:Exception) {
                        Toast.makeText(this,"لم تنجح إزالة الخلفية؛ الصورة الأصلية محفوظة.",Toast.LENGTH_LONG).show()
                    } finally { segmenter.close() }
                }
                .addOnFailureListener {
                    segmenter.close()
                    Toast.makeText(this,"تعذرت المعالجة. تأكد من توفر Google Play services والنموذج، ثم أعد المحاولة.",Toast.LENGTH_LONG).show()
                }
        } catch(e:Exception) {
            segmenter.close()
            Toast.makeText(this,"تعذر فتح الصورة. اختر صورة أخرى.",Toast.LENGTH_LONG).show()
        }
    }
    @Deprecated("Legacy image picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            processedPhoto = null
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                pendingPhoto = uri
                photoPreview?.apply { setImageURI(uri); visibility = View.VISIBLE }
            } catch (_: Exception) {
                Toast.makeText(this, "تعذر حفظ إذن الوصول للصورة، حاول صورة أخرى", Toast.LENGTH_LONG).show()
            }
        }
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

    private fun sellScreen() {
        page("تسجيل بيع قطعة")
        val arr=items()
        var available=0
        for(i in 0 until arr.length()) {
            val item=arr.getJSONObject(i)
            if(item.optInt("quantity") > item.optInt("sold")) {
                available++
                val line=card(item.optString("name"),"المتبقي: ${item.optInt("quantity")-item.optInt("sold")} | سعر البيع: ${fmt.format(item.optDouble("sale"))} د.ت")
                line.setOnClickListener { sell(i) }
                root.addView(line,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(9) })
            }
        }
        if(available==0) root.addView(txt("لا توجد قطع متاحة للبيع. أضف مشتريات أولًا."))
    }
    private fun reports() {
        page("التقارير")
        val arr=items()
        var stock=0; var sold=0; var cost=0.0; var revenue=0.0; var profit=0.0
        for(i in 0 until arr.length()) {
            val item=arr.getJSONObject(i)
            val qty=item.optInt("quantity"); val done=item.optInt("sold")
            stock+=qty-done; sold+=done
            cost+=(qty-done)*item.optDouble("cost")
            revenue+=done*item.optDouble("sale")
            profit+=done*(item.optDouble("sale")-item.optDouble("cost"))
        }
        root.addView(txt("ملخص الأداء",19f,accent))
        rowCards("الربح المحقق" to "${fmt.format(profit)} د.ت","إجمالي المبيعات" to "${fmt.format(revenue)} د.ت",true)
        rowCards("القطع المباعة" to "$sold","المخزون المتبقي" to "$stock")
        root.addView(card("قيمة المخزون بالتكلفة","${fmt.format(cost)} د.ت"))
        root.addView(txt("البيانات المعروضة إجمالية منذ بدء الاستخدام. الرسوم البيانية والتصدير قيد التطوير.",13f,Color.LTGRAY))
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
