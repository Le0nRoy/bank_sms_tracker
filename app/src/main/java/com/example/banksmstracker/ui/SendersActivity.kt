package com.example.banksmstracker.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.serializer.ConfigLoader
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.ConfigRepository

class SendersActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SendersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_senders)

        setupRecyclerView()
        loadSenders()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewSenders)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SendersAdapter()
        recyclerView.adapter = adapter
    }

    private fun loadSenders() {
        try {
            adapter.submitList(ConfigRepository.config.senders)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class SendersAdapter : RecyclerView.Adapter<SendersAdapter.SenderViewHolder>() {
    private var senders: List<Sender> = emptyList()

    fun submitList(newSenders: List<Sender>) {
        senders = newSenders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SenderViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return SenderViewHolder(view)
    }

    override fun onBindViewHolder(holder: SenderViewHolder, position: Int) {
        holder.bind(senders[position])
    }

    override fun getItemCount(): Int = senders.size

    class SenderViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        fun bind(sender: Sender) {
            val text1 = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
            val text2 = itemView.findViewById<android.widget.TextView>(android.R.id.text2)
            
            text1.text = "${sender.name} (${sender.address})"
            text2.text = "Rules: ${sender.rules.size}\n${sender.rules.joinToString("\n") { "• ${it.regex}" }}"
        }
    }
}
