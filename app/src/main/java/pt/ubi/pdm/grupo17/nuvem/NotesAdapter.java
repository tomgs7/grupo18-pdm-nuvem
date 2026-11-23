package pt.ubi.pdm.grupo17.nuvem;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Lista de notas do ecrã principal. */
public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.Holder> {

    public interface OnNoteClick { void onClick(Note note); }

    private final List<Note> items = new ArrayList<>();
    private final OnNoteClick onClick;
    // cores suaves para o avatar, escolhidas a partir do título
    private static final int[] AVATAR_COLORS = {
            Color.parseColor("#CDEBEA"), Color.parseColor("#D9E8C8"), Color.parseColor("#FCE3B8"),
            Color.parseColor("#D6E4F5"), Color.parseColor("#E5D8F0"), Color.parseColor("#F6D5D0")};

    private static String relative(java.util.Date d) {
        long now = System.currentTimeMillis(), t = d.getTime();
        if (now - t < DateUtils.MINUTE_IN_MILLIS) return "agora";
        long res = now - t < DateUtils.DAY_IN_MILLIS ? DateUtils.MINUTE_IN_MILLIS : DateUtils.DAY_IN_MILLIS;
        return DateUtils.getRelativeTimeSpanString(t, now, res).toString();
    }

    public NotesAdapter(OnNoteClick onClick) { this.onClick = onClick; }

    public void submit(List<Note> notes) {
        items.clear();
        items.addAll(notes);
        notifyDataSetChanged(); // lista pequena; numa app real usar ListAdapter + DiffUtil
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Note n = items.get(position);
        String name = n.title.isEmpty() ? "(sem título)" : n.title;
        h.title.setText(name);
        String when = n.updatedAt == null ? "a sincronizar…" : relative(n.updatedAt);
        h.subtitle.setText(n.updatedBy + " · " + when + " · " + n.members.size() + " membro(s)");
        h.pending.setVisibility(n.pending ? View.VISIBLE : View.GONE);
        h.avatar.setText(n.title.isEmpty() ? "?" : n.title.substring(0, 1).toUpperCase());
        h.avatar.setBackgroundTintList(ColorStateList.valueOf(
                AVATAR_COLORS[Math.floorMod(name.hashCode(), AVATAR_COLORS.length)]));
        h.itemView.setOnClickListener(v -> onClick.onClick(n));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView avatar;
        final ImageView pending;

        Holder(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            avatar = v.findViewById(R.id.avatar);
            pending = v.findViewById(R.id.pending);
        }
    }
}
