<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg_primary">

    <!-- Search Bar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="12dp"
        android:gravity="center_vertical"
        android:background="@color/bg_header">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="🔍"
            android:textSize="18sp"
            android:layout_marginEnd="10dp"/>

        <EditText
            android:id="@+id/etSearch"
            android:layout_width="0dp"
            android:layout_height="40dp"
            android:layout_weight="1"
            android:hint="Search channels, movies, series..."
            android:textColorHint="#40FFFFFF"
            android:textColor="#FFFFFF"
            android:textSize="14sp"
            android:background="@drawable/input_bg"
            android:paddingStart="12dp"
            android:paddingEnd="12dp"
            android:imeOptions="actionSearch"
            android:inputType="text"
            android:singleLine="true"/>

        <TextView
            android:id="@+id/btnClear"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:text="✕"
            android:textColor="#80FFFFFF"
            android:textSize="16sp"
            android:gravity="center"
            android:layout_marginStart="8dp"/>

    </LinearLayout>

    <!-- Result count -->
    <TextView
        android:id="@+id/tvResultCount"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="#80FFFFFF"
        android:textSize="11sp"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:paddingTop="8dp"
        android:paddingBottom="4dp"
        android:visibility="gone"/>

    <!-- Loading -->
    <ProgressBar
        android:id="@+id/progressBar"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:layout_marginTop="40dp"
        android:indeterminateTint="#FFB300"
        android:visibility="gone"/>

    <!-- Results list -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvResults"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:padding="8dp"
        android:visibility="gone"/>

    <!-- Empty / hint state -->
    <LinearLayout
        android:id="@+id/emptyState"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="32dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="🔍"
            android:textSize="48sp"
            android:layout_marginBottom="16dp"/>

        <TextView
            android:id="@+id/tvNoResults"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Search across Live TV, Movies and Series"
            android:textColor="#80FFFFFF"
            android:textSize="14sp"
            android:gravity="center"/>

    </LinearLayout>

</LinearLayout>
