package com.example.plantdiseasedetector.activities

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.plantdiseasedetector.R
import com.example.plantdiseasedetector.databinding.ItemDiseaseBinding
import com.mckrpk.animatedprogressbar.AnimatedProgressBar
import com.mckrpk.animatedprogressbar.dpToPx

class DiseaseAdapter(context: Context):RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var ctx = context
    private val differCallback by lazy {
        object : DiffUtil.ItemCallback<Item>(){
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
                return oldItem == newItem
            }

        }
    }
    val differ = AsyncListDiffer(this,differCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiseaseBinding.inflate(LayoutInflater.from(parent.context))
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val disease = differ.currentList[position]
        val diseaseHolder = holder as ViewHolder
        diseaseHolder.binding.diseaseName.text = disease.name
        diseaseHolder.binding.diseasePercent.setTextColor(Color.RED)
        diseaseHolder.binding.diseasePercent.text = String.format(ctx.resources.getString(R.string.ai_confidence_s),disease.confidence.toString())
        val bar = diseaseHolder.binding.animatedProgressBar
        bar.setMax(100)
        bar.setProgress(disease.confidence)
        bar.setProgressTipEnabled(true)
        bar.setAnimDuration(1200)
        bar.setProgressStyle(AnimatedProgressBar.Style.WAVE)
        bar.setLineWidth(dpToPx(5, ctx).toInt())
    }
    override fun getItemCount(): Int {
        return differ.currentList.size
    }

}

class ViewHolder( val binding: ItemDiseaseBinding) :RecyclerView.ViewHolder(binding.root)