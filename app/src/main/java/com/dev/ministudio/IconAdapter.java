package com.dev.ministudio;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.GridView;
import java.util.List;

public class IconAdapter extends BaseAdapter {
    private Context context;
    private List<Integer> iconList; // เปลี่ยนจาก int[] เป็น List<Integer>

    // Constructor รับค่า List
    public IconAdapter(Context context, List<Integer> iconList) {
        this.context = context;
        this.iconList = iconList;
    }

    @Override 
    public int getCount() { 
        return iconList.size(); // ใช้ .size() สำหรับ List
    }

    @Override 
    public Object getItem(int position) { 
        return iconList.get(position); // ใช้ .get() สำหรับ List
    }

    @Override 
    public long getItemId(int position) { 
        return position; 
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView;
        if (convertView == null) {
            imageView = new ImageView(context);
            // ปรับขนาดไอคอนให้พอดี
            imageView.setLayoutParams(new GridView.LayoutParams(150, 150));
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imageView.setPadding(8, 8, 8, 8);
        } else {
            imageView = (ImageView) convertView;
        }
        
        // ดึงไอคอนจากตำแหน่งที่ต้องการ
        imageView.setImageResource(iconList.get(position));
        return imageView;
    }
}
