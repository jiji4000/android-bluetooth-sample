package jiji4000.jp.androidbluetoothsample

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.item_central_list.view.*

class CentralListAdapter(centralList: ArrayList<DeviceData>, private val centralListAdapterListener: PeripheralActivity.CentralListAdapterListener) : RecyclerView.Adapter<CentralListAdapter.ViewHolder>() {

    var centralList: ArrayList<DeviceData> = centralList
        set(centralList) {
            field = centralList
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int {
        return centralList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindPlayList(centralList[position])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_central_list, parent, false)
        return ViewHolder(view, centralListAdapterListener)
    }

    class ViewHolder(view: View, val centralListAdapter: PeripheralActivity.CentralListAdapterListener)
        : RecyclerView.ViewHolder(view) {

        fun bindPlayList(centralList: DeviceData) {
            with(centralList) {
                // title
                itemView.uuid.text = uuid
                // message
                itemView.message.text = message
                // click listener
                itemView.setOnClickListener {
                    centralListAdapter.onClick(this)
                }
            }
        }
    }
}
