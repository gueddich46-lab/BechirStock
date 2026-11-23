package com.bechir.stock

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.MotionEvent
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
    private var restoreCutout: Bitmap? = null
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
    private fun saleRecords(): JSONArray = try { JSONArray(data.getString("sale_records","[]")) } catch (_:Exception) { JSONArray() }
    private fun saveSales(records: JSONArray) { data.edit().putString("sale_records",records.toString()).apply() }
    private fun revenueFor(item: JSONObject): Double {
        val records=item.optJSONArray("sales")
        if(records==null) return item.optInt("sold")*item.optDouble("sale")
        var total=0.0
        for(i in 0 until records.length()) total+=records.getJSONObject(i).optDouble("price")
        return total
    }
    private fun totalRevenue(): Double {
        val arr=items()
        var sum=0.0
        for(i in 0 until arr.length()) sum+=revenueFor(arr.getJSONObject(i))
        return sum
    }
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
        var stock = 0; var sold = 0; var cost = 0.0; var revenue = 0.0; var profit = 0.0; var expectedProfit = 0.0
        for (i in 0 until arr.length()) {
            val it = arr.getJSONObject(i)
            val quantity = it.optInt("quantity", 1); val soldQty = it.optInt("sold", 0)
            val unitCost = it.optDouble("cost", 0.0); val sale = it.optDouble("sale", 0.0)
            stock += quantity - soldQty; sold += soldQty; cost += (quantity - soldQty) * unitCost
            expectedProfit += (quantity - soldQty) * (sale - unitCost)
            revenue += revenueFor(it); profit += revenueFor(it) - soldQty*unitCost
        }
        root.addView(txt("ملخص تجارتك", 16f, accent))
        rowCards("الربح المحقق" to "${fmt.format(profit)} د.ت", "المبيعات" to "$sold قطعة",true)
        rowCards("المخزون" to "$stock قطعة", "قيمة المخزون" to "${fmt.format(cost)} د.ت")
        rowCards("الربح المتوقع للمخزون" to "${fmt.format(expectedProfit)} د.ت", "إجمالي المبيعات" to "${fmt.format(revenue)} د.ت")
        menu()
        root.addView(txt("جميع بياناتك محفوظة محليًا على هذا الهاتف.",13f,Color.LTGRAY))
    }
    private fun addPurchase() {
        page("إضافة شراء جديد")
        pendingPhoto=null
        processedPhoto=null
        restoreCutout=null
        photoPreview=null

        fun section(title:String): LinearLayout {
            val panelView=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                background=shape(soft,18)
                setPadding(dp(12),dp(12),dp(12),dp(12))
            }
            panelView.addView(txt(title,17f,accent))
            root.addView(panelView,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
            return panelView
        }
        fun addField(group:LinearLayout,label:String,number:Boolean=false):EditText {
            group.addView(txt(label,13f,Color.rgb(201,190,216)))
            return field(label,number).apply {
                textSize=16f
                gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
                background=shape(Color.rgb(48,39,68),12)
                setPadding(dp(12),dp(10),dp(12),dp(10))
                group.addView(this,LinearLayout.LayoutParams(-1,dp(48)).apply { bottomMargin=dp(9) })
            }
        }
        val info=section("تفاصيل البضاعة")
        val options=arrayOf("بالقطعة","بالبالة / الكيلو")
        info.addView(txt("طريقة الشراء",13f,Color.rgb(201,190,216)))
        val mode=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options)
            background=shape(Color.rgb(48,39,68),12)
            setPadding(dp(10),0,dp(10),0)
        }
        info.addView(mode,LinearLayout.LayoutParams(-1,dp(50)))
        val name=addField(info,"اسم المنتج أو البالة")
        val category=addField(info,"التصنيف / الماركة")
        val size=addField(info,"القياس (مثل 42 أو XL)")

        val prices=section("الكميات والأسعار")
        val quantity=addField(prices,"عدد القطع",true)
        val cost=addField(prices,"ثمن الشراء الإجمالي بالدينار",true)
        val expenses=addField(prices,"مصاريف إضافية (نقل...) بالدينار",true)
        val sale=addField(prices,"سعر بيع القطعة بالدينار",true)
        val preview=txt("التكلفة لكل قطعة: —",17f,Color.rgb(103,240,157))
        prices.addView(preview)
        val potential=txt("الربح المتوقع لكل قطعة: —",16f,accent)
        prices.addView(potential)
        val recalc={
            val qty=quantity.text.toString().toIntOrNull() ?: 0
            val total=amount(cost.text.toString())+amount(expenses.text.toString())
            if(qty>0 && cost.text.isNotBlank()) {
                val unit=total/qty
                preview.text="التكلفة لكل قطعة: ${fmt.format(unit)} د.ت"
                potential.text="الربح المتوقع لكل قطعة: ${fmt.format(amount(sale.text.toString())-unit)} د.ت"
            } else {
                preview.text="التكلفة لكل قطعة: —"
                potential.text="الربح المتوقع لكل قطعة: —"
            }
        }
        val watcher=object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { recalc() }
            override fun afterTextChanged(s:android.text.Editable?) {}
        }
        listOf(quantity,cost,expenses,sale).forEach { it.addTextChangedListener(watcher) }

        val pictures=section("صورة المنتج")
        pictures.addView(button("📷 اختيار صورة المنتج") {
            val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type="image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent,101)
        },LinearLayout.LayoutParams(-1,dp(50)))
        photoPreview=ImageView(this).apply {
            adjustViewBounds=true
            scaleType=ImageView.ScaleType.FIT_CENTER
            visibility=View.GONE
        }
        pictures.addView(photoPreview,LinearLayout.LayoutParams(-1,dp(190)))
        pictures.addView(button("✨ إزالة الخلفية وجعلها بيضاء") { removeBackground() })
        pictures.addView(button("🖌 تصحيح الحواف يدويًا") { manualCorrection() })
        pictures.addView(button("↩ استعادة الصورة الأصلية") {
            processedPhoto=null
            pendingPhoto?.let { photoPreview?.setImageURI(it) }
        })

        root.addView(button("✓ حفظ المشتريات") {
            val qty=quantity.text.toString().toIntOrNull() ?: 0
            val price=amount(cost.text.toString())
            val extra=amount(expenses.text.toString())
            val salePrice=amount(sale.text.toString())
            if(name.text.isBlank() || qty<1 || cost.text.isBlank() || price<0 || extra<0 || salePrice<0 ||
                listOf(cost,expenses,sale).any { it.text.toString().isNotBlank() && it.text.toString().trim().replace(",",".").toDoubleOrNull()==null }) {
                Toast.makeText(this,"تحقق من الاسم والعدد والأسعار",Toast.LENGTH_LONG).show()
            } else {
                val arr=items()
                arr.put(JSONObject().put("name",name.text.toString()).put("category",category.text.toString())
                    .put("quantity",qty).put("sold",0).put("cost",(price+extra)/qty)
                    .put("sale",salePrice).put("size",size.text.toString().trim())
                    .put("mode",options[mode.selectedItemPosition])
                    .put("photo",processedPhoto ?: pendingPhoto?.toString() ?: ""))
                save(arr)
                home()
            }
        },LinearLayout.LayoutParams(-1,dp(56)).apply { bottomMargin=dp(10) })
        root.addView(button("رجوع دون حفظ") { home() },LinearLayout.LayoutParams(-1,dp(48)))
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
            if (remaining > 0) product.addView(txt("الربح المتوقع للقطعة: ${fmt.format(item.optDouble("sale") - item.optDouble("cost"))} د.ت",15f,accent))
            product.addView(button("📷 تعديل صورة القطعة") { editPhoto(i) })
            if (remaining > 0) product.addView(button("تسجيل بيع قطعة") { sell(i) })
            root.addView(product, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        }
    }


    private fun editPhoto(index: Int) {
        val item = items().getJSONObject(index)
        val originalUri = item.optString("photo")
        if(originalUri.isBlank()) {
            Toast.makeText(this,"لا توجد صورة مسجلة لهذه القطعة",Toast.LENGTH_LONG).show()
            return
        }
        pendingPhoto = Uri.parse(originalUri)
        processedPhoto = null
        page("تحسين صورة القطعة")
        root.addView(txt(item.optString("name"),20f))
        photoPreview = ImageView(this).apply {
            scaleType=ImageView.ScaleType.FIT_CENTER
            adjustViewBounds=true
            try { setImageURI(pendingPhoto) } catch (_:Exception) {}
        }
        root.addView(photoPreview,LinearLayout.LayoutParams(-1,dp(240)))
        root.addView(button("✨ إزالة الخلفية وجعلها بيضاء") { removeBackground() })
        root.addView(button("🖌 تصحيح الحواف يدويًا") { manualCorrection() })
        root.addView(button("↩ استعادة الصورة الأصلية") {
            processedPhoto=null
            photoPreview?.setImageURI(Uri.parse(originalUri))
        })
        root.addView(button("حفظ الصورة الجديدة") {
            val edited=processedPhoto
            if(edited.isNullOrBlank()) {
                Toast.makeText(this,"عالج الصورة أولًا، أو اضغط رجوع",Toast.LENGTH_LONG).show()
            } else {
                val arr=items()
                arr.getJSONObject(index).put("photo",edited)
                save(arr)
                inventory()
            }
        })
        root.addView(button("رجوع") { inventory() })
    }

    // Removes only very small detached mask fragments. Large details (laces, logos) are retained.
    // Keep the largest connected foreground region. This removes detached background flecks
    // while leaving the main product intact; use the original when the mask is unreliable.
    private fun cleanCutout(source: Bitmap): Bitmap {
        val w=source.width; val h=source.height
        val pixels=IntArray(w*h)
        source.getPixels(pixels,0,w,0,0,w,h)
        val visited=BooleanArray(pixels.size)
        val queue=IntArray(pixels.size)
        var largestStart=-1
        var largestSize=0
        for(start in pixels.indices) {
            if(visited[start] || Color.alpha(pixels[start])<40) continue
            var head=0; var tail=1
            queue[0]=start
            visited[start]=true
            while(head<tail) {
                val p=queue[head++]; val x=p%w; val y=p/w
                if(x>0) { val n=p-1; if(!visited[n] && Color.alpha(pixels[n])>=40) {visited[n]=true;queue[tail++]=n} }
                if(x+1<w) { val n=p+1; if(!visited[n] && Color.alpha(pixels[n])>=40) {visited[n]=true;queue[tail++]=n} }
                if(y>0) { val n=p-w; if(!visited[n] && Color.alpha(pixels[n])>=40) {visited[n]=true;queue[tail++]=n} }
                if(y+1<h) { val n=p+w; if(!visited[n] && Color.alpha(pixels[n])>=40) {visited[n]=true;queue[tail++]=n} }
            }
            if(tail>largestSize) {
                largestSize=tail
                largestStart=start
            }
        }
        if(largestStart<0 || largestSize<maxOf(100,pixels.size/1000)) return source
        // Flood fill from the largest component, then retain its soft alpha fringe.
        val keep=BooleanArray(pixels.size)
        var head=0; var tail=1
        queue[0]=largestStart
        keep[largestStart]=true
        while(head<tail) {
            val p=queue[head++]; val x=p%w; val y=p/w
            if(x>0) { val n=p-1; if(!keep[n] && Color.alpha(pixels[n])>=40) {keep[n]=true;queue[tail++]=n} }
            if(x+1<w) { val n=p+1; if(!keep[n] && Color.alpha(pixels[n])>=40) {keep[n]=true;queue[tail++]=n} }
            if(y>0) { val n=p-w; if(!keep[n] && Color.alpha(pixels[n])>=40) {keep[n]=true;queue[tail++]=n} }
            if(y+1<h) { val n=p+w; if(!keep[n] && Color.alpha(pixels[n])>=40) {keep[n]=true;queue[tail++]=n} }
        }
        // A one-pixel fringe retains antialiasing at the boundary.
        for(i in pixels.indices) {
            if(keep[i]) continue
            val x=i%w; val y=i/w
            val adjacent=(x>0 && keep[i-1]) || (x+1<w && keep[i+1]) ||
                (y>0 && keep[i-w]) || (y+1<h && keep[i+w])
            if(!adjacent) pixels[i]=Color.TRANSPARENT
        }
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels,0,w,0,0,w,h)
        }
    }
    private fun onWhiteCanvas(source: Bitmap, framing: Bitmap = source): Bitmap {
        val w=source.width; val h=source.height
        val pixels=IntArray(w*h)
        framing.getPixels(pixels,0,w,0,0,w,h)
        var left=w; var top=h; var right=-1; var bottom=-1
        for(y in 0 until h) for(x in 0 until w) {
            if(Color.alpha(pixels[y*w+x])>24) {
                if(x<left) left=x
                if(x>right) right=x
                if(y<top) top=y
                if(y>bottom) bottom=y
            }
        }
        if(right<left || bottom<top) throw IllegalStateException("لم يتم العثور على المنتج")
        val margin=0.07f
        val out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(out)
        canvas.drawColor(Color.WHITE)
        val bw=(right-left+1).toFloat(); val bh=(bottom-top+1).toFloat()
        val scale=minOf(w*(1f-2*margin)/bw,h*(1f-2*margin)/bh)
        val dstW=bw*scale; val dstH=bh*scale
        val dest=RectF((w-dstW)/2f,(h-dstH)/2f,(w+dstW)/2f,(h+dstH)/2f)
        canvas.drawBitmap(source,android.graphics.Rect(left,top,right+1,bottom+1),dest,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun manualCorrection() {
        val edited=processedPhoto
        if(edited.isNullOrBlank()) {
            Toast.makeText(this,"نفّذ العزل التلقائي أولًا",Toast.LENGTH_LONG).show()
            return
        }
        val current=try {
            contentResolver.openInputStream(Uri.parse(edited))?.use { BitmapFactory.decodeStream(it) }
        } catch (_:Exception) { null }
        if(current==null) {
            Toast.makeText(this,"تعذر فتح الصورة المعدلة",Toast.LENGTH_LONG).show()
            return
        }
        val working=current.copy(Bitmap.Config.ARGB_8888,true)
        val restoreSource=restoreCutout?.takeIf { it.width==working.width && it.height==working.height }
        val history=ArrayList<Bitmap>()
        var restore=false
        var radius=12f
        var zoom=1f
        var panX=0f
        var panY=0f
        val drawing=object: View(this) {
            private val rect=RectF()
            private var lastX=0f
            private var lastY=0f
            private var dragging=false
            private var lastFocusX=0f
            private var lastFocusY=0f
            private fun imageRect(): RectF {
                val scale=minOf(width.toFloat()/working.width,height.toFloat()/working.height)*zoom
                val dw=working.width*scale; val dh=working.height*scale
                rect.set((width-dw)/2f+panX,(height-dh)/2f+panY,(width+dw)/2f+panX,(height+dh)/2f+panY)
                return rect
            }
            override fun onDraw(c:Canvas) {
                super.onDraw(c)
                c.drawColor(Color.rgb(236,236,236))
                c.drawBitmap(working,null,imageRect(),Paint(Paint.FILTER_BITMAP_FLAG))
            }
            private fun paintAt(x:Float,y:Float) {
                val area=imageRect()
                if(!area.contains(x,y)) return
                val bx=(x-area.left)*working.width/area.width()
                val by=(y-area.top)*working.height/area.height()
                val r=radius*working.width/area.width()
                val left=maxOf(0,(bx-r-2).toInt())
                val top=maxOf(0,(by-r-2).toInt())
                val right=minOf(working.width-1,(bx+r+2).toInt())
                val bottom=minOf(working.height-1,(by+r+2).toInt())
                val source=if(restore) restoreSource else null
                for(py in top..bottom) for(px in left..right) {
                    val dx=px-bx; val dy=py-by
                    if(dx*dx+dy*dy<=r*r) {
                        if(restore) {
                            if(source!=null) working.setPixel(px,py,source.getPixel(px,py))
                        } else working.setPixel(px,py,Color.WHITE)
                    }
                }
                invalidate()
            }
            override fun onTouchEvent(event:MotionEvent):Boolean {
                if(event.pointerCount>=2) {
                    val fx=(event.getX(0)+event.getX(1))/2f
                    val fy=(event.getY(0)+event.getY(1))/2f
                    if(event.actionMasked==MotionEvent.ACTION_POINTER_DOWN) {
                        lastFocusX=fx; lastFocusY=fy
                    } else if(event.actionMasked==MotionEvent.ACTION_MOVE) {
                        panX+=fx-lastFocusX; panY+=fy-lastFocusY
                        lastFocusX=fx;lastFocusY=fy
                        invalidate()
                    }
                    dragging=false
                    return true
                }
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if(history.size>=8) history.removeAt(0).recycle()
                        history.add(working.copy(Bitmap.Config.ARGB_8888,false))
                        dragging=true
                        lastX=event.x;lastY=event.y
                        paintAt(lastX,lastY)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if(!dragging) return true
                        val x=event.x;val y=event.y
                        val steps=maxOf(1,(kotlin.math.hypot(x-lastX,y-lastY)/3f).toInt())
                        for(i in 1..steps) {
                            val t=i.toFloat()/steps
                            paintAt(lastX+(x-lastX)*t,lastY+(y-lastY)*t)
                        }
                        lastX=x;lastY=y
                        return true
                    }
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> { dragging=false;return true }
                }
                return true
            }
            fun undo() {
                if(history.isEmpty()) return
                val previous=history.removeAt(history.lastIndex)
                Canvas(working).drawBitmap(previous,0f,0f,null)
                previous.recycle()
                invalidate()
            }
            fun changeZoom(value:Float) { zoom=value;panX=0f;panY=0f;invalidate() }
        }
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(10),dp(10),dp(10),dp(10))
        }
        layout.addView(txt("كبّر الصورة، حرّكها بإصبعين، ثم صحّح الحواف بإصبع واحد.",13f,Color.DKGRAY))
        layout.addView(drawing,LinearLayout.LayoutParams(-1,dp(320)))
        layout.addView(txt("التكبير: 1× إلى 5×",13f,Color.DKGRAY))
        layout.addView(SeekBar(this).apply {
            max=40
            progress=0
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) { drawing.changeZoom(1f+value/10f) }
                override fun onStartTrackingTouch(bar:SeekBar?) {}
                override fun onStopTrackingTouch(bar:SeekBar?) {}
            })
        })
        val modes=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        modes.addView(button("ممحاة") { restore=false },LinearLayout.LayoutParams(0,dp(48),1f))
        modes.addView(button("استرجاع جزء") {
            if(restoreSource==null) Toast.makeText(this,"أعد العزل لتفعيل استرجاع التفاصيل",Toast.LENGTH_LONG).show()
            else restore=true
        },LinearLayout.LayoutParams(0,dp(48),1f))
        layout.addView(modes)
        layout.addView(txt("حجم الفرشاة",13f,Color.DKGRAY))
        layout.addView(SeekBar(this).apply {
            max=65;progress=9
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) { radius=(value+3).toFloat() }
                override fun onStartTrackingTouch(bar:SeekBar?) {}
                override fun onStopTrackingTouch(bar:SeekBar?) {}
            })
        })
        layout.addView(button("تراجع عن آخر تعديل") { drawing.undo() })
        val dialog=AlertDialog.Builder(this).setTitle("تصحيح الصورة يدويًا")
            .setView(layout).setNegativeButton("إلغاء",null)
            .setPositiveButton("اعتماد التصحيح",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val file=java.io.File(filesDir,"stock_manual_"+System.currentTimeMillis()+".png")
                    file.outputStream().use { working.compress(Bitmap.CompressFormat.PNG,100,it) }
                    processedPhoto=Uri.fromFile(file).toString()
                    photoPreview?.setImageBitmap(working)
                    dialog.dismiss()
                } catch (_:Exception) {
                    Toast.makeText(this,"تعذر حفظ الصورة المصححة",Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
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
                        val cleaned = cleanCutout(foreground)
                        restoreCutout = onWhiteCanvas(foreground,cleaned)
                        val output = onWhiteCanvas(cleaned)
                        val file = java.io.File(filesDir,"stock_product_"+System.currentTimeMillis()+".png")
                        file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG,100,it) }
                        processedPhoto = Uri.fromFile(file).toString()
                        photoPreview?.apply { setImageBitmap(output); visibility=View.VISIBLE }
                        Toast.makeText(this,"تمت محاولة إزالة الأجزاء المنفصلة وتوسيط المنتج. راجع الأربطة والحواف قبل الحفظ.",Toast.LENGTH_LONG).show()
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
        val arr=items()
        val item=arr.getJSONObject(index)
        val remaining=item.optInt("quantity")-item.optInt("sold")
        if(remaining<=0) { inventory();return }
        val form=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(8),dp(20),0)
        }
        form.addView(txt("المنتج: ${item.optString("name")}",17f,Color.DKGRAY))
        form.addView(txt("تكلفة القطعة: ${fmt.format(item.optDouble("cost"))} د.ت",15f,Color.DKGRAY))
        val actualPrice=EditText(this).apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(item.optDouble("sale").toString())
            hint="سعر البيع الفعلي بالدينار"
            selectAll()
        }
        form.addView(actualPrice)
        val result=txt("الربح المتوقع: ${fmt.format(item.optDouble("sale")-item.optDouble("cost"))} د.ت",16f,Color.DKGRAY)
        form.addView(result)
        actualPrice.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                val value=s.toString().replace(",",".").toDoubleOrNull()
                result.text=if(value==null) "أدخل سعرًا صحيحًا" else "الربح الفعلي: ${fmt.format(value-item.optDouble("cost"))} د.ت"
            }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        val dialog=AlertDialog.Builder(this).setTitle("تسجيل بيع قطعة")
            .setView(form).setNegativeButton("إلغاء",null)
            .setPositiveButton("تأكيد البيع",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val price=actualPrice.text.toString().trim().replace(",",".").toDoubleOrNull()
                if(price==null || !price.isFinite() || price<0) {
                    actualPrice.error="أدخل سعر بيع صحيحًا"
                } else {
                    val fresh=items()
                    val current=fresh.getJSONObject(index)
                    if(current.optInt("sold")>=current.optInt("quantity")) {
                        Toast.makeText(this,"نفدت الكمية",Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        inventory()
                    } else {
                        val date=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.getDefault()).format(java.util.Date())
                        val sales=current.optJSONArray("sales") ?: JSONArray().also { existing ->
                            // Preserve historical sales recorded by earlier versions.
                            repeat(current.optInt("sold")) { existing.put(JSONObject().put("price",current.optDouble("sale")).put("date","قبل التحديث")) }
                        }
                        sales.put(JSONObject().put("price",price).put("date",date))
                        current.put("sales",sales)
                        current.put("sold",current.optInt("sold")+1)
                        save(fresh)
                        dialog.dismiss()
                        inventory()
                    }
                }
            }
        }
        dialog.show()
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
            revenue+=revenueFor(item)
            profit+=revenueFor(item)-done*item.optDouble("cost")
        }
        root.addView(txt("ملخص الأداء",19f,accent))
        rowCards("الربح المحقق" to "${fmt.format(profit)} د.ت","إجمالي المبيعات" to "${fmt.format(revenue)} د.ت",true)
        rowCards("القطع المباعة" to "$sold","المخزون المتبقي" to "$stock")
        root.addView(card("قيمة المخزون بالتكلفة","${fmt.format(cost)} د.ت"))
        root.addView(txt("البيانات المعروضة إجمالية منذ بدء الاستخدام. الرسوم البيانية والتصدير قيد التطوير.",13f,Color.LTGRAY))
    }
    private fun sales() {
        page("المبيعات والأرباح")
        val arr=items()
        var revenue=0.0; var profit=0.0; var count=0
        for(i in 0 until arr.length()) {
            val item=arr.getJSONObject(i)
            val sold=item.optInt("sold")
            if(sold==0) continue
            val earned=revenueFor(item)
            val itemProfit=earned-sold*item.optDouble("cost")
            count+=sold;revenue+=earned;profit+=itemProfit
            val panelView=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                background=shape(soft)
                setPadding(dp(12),dp(10),dp(12),dp(10))
            }
            panelView.addView(txt(item.optString("name")+" — $sold قطعة",18f))
            panelView.addView(txt("المبيعات: ${fmt.format(earned)} د.ت  |  الربح: ${fmt.format(itemProfit)} د.ت",15f,Color.rgb(103,240,157)))
            val history=item.optJSONArray("sales")
            if(history!=null) for(j in history.length()-1 downTo 0) {
                val record=history.optJSONObject(j) ?: continue
                panelView.addView(txt("${record.optString("date")}  •  ${fmt.format(record.optDouble("price"))} د.ت",13f,Color.LTGRAY))
            }
            root.addView(panelView,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        }
        rowCards("إجمالي المبيعات" to "${fmt.format(revenue)} د.ت","الربح المحقق" to "${fmt.format(profit)} د.ت",true)
        root.addView(txt("القطع المباعة: $count",17f))
        if(count==0) root.addView(txt("لا توجد مبيعات مسجلة بعد."))
    }
}
