package com.yzyfdf.crawler

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.seimicrawler.xpath.JXDocument

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getData()
    }

    private fun getData() {
        Thread {
            val start = System.currentTimeMillis()
            val hotSearch = getHotSearch()
            val end = System.currentTimeMillis()
            println("耗时 = ${end - start} 毫秒")
            runOnUiThread {
                setData(hotSearch)
            }
        }.start()
    }

    private fun setData(list: List<String>) {
        tv.text = list.reduce { acc, s -> "${acc}\n${s}" }
    }

    private fun getHotSearch(): List<String> {
        return try {
            val document: Document = Jsoup.connect("https://www.taobao.com/")
                    .validateTLSCertificates(false)
                    .get()
            val jxDocument = JXDocument.create(document)
            jxDocument.selN("//div[@class='search-hots-fline']/a/text()")
                    .map { it.toString() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}