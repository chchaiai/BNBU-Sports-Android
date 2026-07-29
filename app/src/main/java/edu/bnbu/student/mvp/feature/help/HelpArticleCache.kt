package edu.bnbu.student.mvp.feature.help

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.bnbu.student.mvp.core.network.HelpArticleResponse

/** Stores the last successfully fetched public help-article payload. */
internal class HelpArticleCache(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE
    )

    fun load(): List<HelpArticleResponse> = try {
        val json = preferences.getString(ArticlesKey, null) ?: return emptyList()
        gson.fromJson<List<HelpArticleResponse>>(json, articlesType) ?: emptyList()
    } catch (_: RuntimeException) {
        // A malformed or old cache must never prevent the help centre opening.
        emptyList()
    }

    fun save(articles: List<HelpArticleResponse>) {
        try {
            preferences.edit().putString(ArticlesKey, gson.toJson(articles)).commit()
        } catch (_: RuntimeException) {
            // The online result remains usable even if its offline copy cannot be saved.
        }
    }

    private companion object {
        const val PreferencesName = "bnbu.student.help_articles.v1"
        const val ArticlesKey = "articles"

        val gson = Gson()
        val articlesType = object : TypeToken<List<HelpArticleResponse>>() {}.type
    }
}
