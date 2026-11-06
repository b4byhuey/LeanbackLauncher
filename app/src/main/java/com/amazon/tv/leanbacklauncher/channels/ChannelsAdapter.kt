package com.amazon.tv.leanbacklauncher.channels

import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences
import com.amazon.tv.leanbacklauncher.MainActivity
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.RoundedRectOutlineProvider
import com.amazon.tv.leanbacklauncher.animation.ViewFocusAnimator
import com.bumptech.glide.Glide
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Baris layar utama berisi program dari Channels / Watch Next aplikasi yang diaktifkan pengguna.
 * Baris otomatis tersembunyi (hideIfEmpty) bila tidak ada program.
 */
class ChannelsAdapter(private val activity: MainActivity) :
    RecyclerView.Adapter<ChannelsAdapter.CardHolder>() {

    class CardHolder(val card: FrameLayout, val poster: ImageView, val title: TextView) :
        RecyclerView.ViewHolder(card)

    private val items = ArrayList<TvChannelsRepository.Program>()
    private val main = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null
    private var generation = 0
    private var observer: ContentObserver? = null
    private val reload = Runnable { refresh() }

    init {
        setHasStableIds(true)
    }

    fun onUiVisible() {
        registerObserver()
        refresh()
    }

    fun onStopUi() {
        main.removeCallbacks(reload)
        observer?.let { activity.contentResolver.unregisterContentObserver(it) }
        observer = null
        generation++ // abaikan hasil yang masih berjalan
        executor?.shutdownNow()
        executor = null
    }

    fun refresh() {
        if (!TvChannelsRepository.hasPermission(activity)) {
            requestPermissionOnce()
            submit(emptyList())
            return
        }
        val myGeneration = ++generation
        val exec = executor?.takeIf { !it.isShutdown }
            ?: Executors.newSingleThreadExecutor().also { executor = it }
        exec.execute {
            val loaded = try {
                TvChannelsRepository.loadEnabledPrograms(activity)
            } catch (e: Exception) {
                Log.w(TAG, "load failed: ${e.message}")
                emptyList()
            }
            main.post { if (myGeneration == generation) submit(loaded) }
        }
    }

    private fun registerObserver() {
        if (observer != null) return
        val obs = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                main.removeCallbacks(reload)
                main.postDelayed(reload, 1500L) // debounce
            }
        }
        try {
            TvChannelsRepository.OBSERVED_URIS.forEach {
                activity.contentResolver.registerContentObserver(it, true, obs)
            }
            observer = obs
        } catch (e: Exception) {
            Log.w(TAG, "observer failed: ${e.message}")
        }
    }

    private fun submit(newItems: List<TvChannelsRepository.Program>) {
        if (newItems == items) return
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun requestPermissionOnce() {
        if (permissionRequested) return
        permissionRequested = true
        try {
            ActivityCompat.requestPermissions(
                activity, arrayOf(TvChannelsRepository.PERMISSION_READ_EPG), REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.w(TAG, "permission request failed: ${e.message}")
        }
    }

    // ---- RecyclerView ----

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].stableId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
        val res = activity.resources
        val scale = RowPreferences.getBannersSize(activity)
        val width = res.getDimensionPixelSize(R.dimen.banner_width) * scale / 100
        val height = res.getDimensionPixelSize(R.dimen.banner_height) * scale / 100

        val poster = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }
        val title = TextView(activity).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            val pad = (8 * res.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, 0xB3000000.toInt())
            )
        }
        val card = FrameLayout(activity).apply {
            layoutParams = RecyclerView.LayoutParams(width, height)
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundResource(R.drawable.banner_background)
            outlineProvider = RoundedRectOutlineProvider(RowPreferences.getCorners(activity).toFloat())
            clipToOutline = true
            addView(poster, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(title, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
        ViewFocusAnimator(card) // animasi fokus yang sama dengan banner aplikasi
        return CardHolder(card, poster, title)
    }

    override fun onBindViewHolder(holder: CardHolder, position: Int) {
        val program = items[position]
        holder.title.text = program.title
        holder.title.visibility = if (program.title.isBlank()) View.GONE else View.VISIBLE
        if (program.posterUri != null) {
            Glide.with(holder.poster).load(program.posterUri).centerCrop().into(holder.poster)
        } else {
            Glide.with(holder.poster).clear(holder.poster)
            holder.poster.setImageDrawable(null)
        }
        holder.card.setOnClickListener { launch(program) }
        holder.card.contentDescription = program.title
    }

    override fun onViewRecycled(holder: CardHolder) {
        Glide.with(holder.poster).clear(holder.poster)
        super.onViewRecycled(holder)
    }

    private fun launch(program: TvChannelsRepository.Program) {
        try {
            val intent = Intent.parseUri(program.intentUri, Intent.URI_INTENT_SCHEME)
            // intent berasal dari aplikasi lain: buang selector dan flag pemberian izin URI
            intent.selector = null
            intent.flags = (intent.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()) or Intent.FLAG_ACTIVITY_NEW_TASK
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "cannot launch ${program.packageName}: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        private const val TAG = "TvChannels"
        const val REQUEST_CODE = 1985
        @Volatile private var permissionRequested = false
    }
}
