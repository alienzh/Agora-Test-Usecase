package io.agora.mediarelay.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import io.agora.mediarelay.R;

public class PopAdapter extends ArrayAdapter<String> {
    private String[] data;
    private Context context;
    private OnItemClickListener listener;
    private int selectIndex = 0;

    public PopAdapter(@NonNull Context context, int resource, String[] objects, int selectIndex) {
        super(context, resource, objects);
        this.data = objects;
        this.context = context;
        this.selectIndex = selectIndex;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        String ss = getItem(position);//获得数据的实例
        View view;
        ViewHolder holder;
        if (convertView == null) {//反复利用布局
            view = LayoutInflater.from(getContext()).inflate(R.layout.pop_item_layout, null);
            holder = new ViewHolder();
            holder.text = view.findViewById(R.id.text);
            view.setTag(holder);
        } else {
            view = convertView;
            holder = (ViewHolder) view.getTag();
        }
        if (selectIndex == position) {
            holder.text.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.color_ffdb00, null));
        } else {
            holder.text.setTextColor(ResourcesCompat.getColor(context.getResources(), R.color.black_overlay, null));
        }
        holder.text.setText(ss);
        holder.text.setOnClickListener(v -> {
            if (listener != null) {
                listener.OnItemClick(position, ss);
            }
        });
        return view;
    }

    static class ViewHolder {
        TextView text;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public interface OnItemClickListener {
        void OnItemClick(int position, String count);
    }
}
