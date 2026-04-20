package com.example.gestura

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.gestura.databinding.FragmentTranslationBinding
import com.translated.lara.Credentials
import com.translated.lara.translator.TranslateOptions
import com.translated.lara.translator.TranslationStyle
import com.translated.lara.translator.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class TranslationFragment : Fragment() {

    private var _binding: FragmentTranslationBinding? = null
    private val binding get() = _binding!!

    private var tts: TextToSpeech? = null
    
    private val laraClient: Translator by lazy {
        val creds = Credentials(
            BuildConfig.LARA_ACCESS_KEY_ID,
            BuildConfig.LARA_ACCESS_KEY_SECRET
        )
        Translator(creds)
    }

    private data class Lang(val code: String, val name: String, val flag: String, val locale: String)
    private val languages = listOf(
        Lang("en", "English", "🇺🇸", "en-US"),
        Lang("es", "Spanish", "🇪🇸", "es-ES"),
        Lang("fr", "French", "🇫🇷", "fr-FR"),
        Lang("de", "German", "🇩🇪", "de-DE"),
        Lang("it", "Italian", "🇮🇹", "it-IT"),
        Lang("pt", "Portuguese", "🇵🇹", "pt-PT"),
        Lang("ja", "Japanese", "🇯🇵", "ja-JP"),
        Lang("zh", "Chinese", "🇨🇳", "zh-CN")
    )

    private var sourceLangCode = "en"
    private var targetLangCode = "es"

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                binding.etInput.setText(text)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTranslationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        setupListeners()

        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    private fun setupSpinners() {
        val langNames = languages.map { "${it.flag} ${it.name}" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, langNames)
        
        binding.spSourceLang.adapter = adapter
        binding.spTargetLang.adapter = adapter

        binding.spSourceLang.setSelection(languages.indexOfFirst { it.code == "en" })
        binding.spTargetLang.setSelection(languages.indexOfFirst { it.code == "es" })

        binding.spSourceLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                sourceLangCode = languages[position].code
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.spTargetLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                targetLangCode = languages[position].code
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        binding.btnSwap.setOnClickListener {
            val oldSource = binding.spSourceLang.selectedItemPosition
            binding.spSourceLang.setSelection(binding.spTargetLang.selectedItemPosition)
            binding.spTargetLang.setSelection(oldSource)
            
            val inputText = binding.etInput.text.toString()
            val resultText = binding.tvResult.text.toString()
            if (resultText.isNotBlank()) {
                binding.etInput.setText(resultText)
                binding.tvResult.text = inputText
            }
        }

        binding.btnVoice.setOnClickListener {
            startVoiceInput(sourceLangCode)
        }

        binding.btnTranslate.setOnClickListener {
            translateText()
        }

        binding.btnClear.setOnClickListener {
            binding.etInput.text.clear()
            binding.cardResult.isVisible = false
            binding.btnClear.isVisible = false
        }

        binding.btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("translation", binding.tvResult.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.btnPlaySource.setOnClickListener {
            speakText(binding.etInput.text.toString(), sourceLangCode)
        }

        binding.btnPlayTarget.setOnClickListener {
            speakText(binding.tvResult.text.toString(), targetLangCode)
        }

        binding.etInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                binding.btnClear.isVisible = !p0.isNullOrBlank()
                binding.btnPlaySource.isVisible = !p0.isNullOrBlank()
            }
            override fun afterTextChanged(p0: android.text.Editable?) {}
        })
    }

    private fun translateText() {
        val text = binding.etInput.text.toString().trim()
        if (text.isEmpty()) {
            showError("Please enter text to translate.")
            return
        }

        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            binding.btnTranslate.isEnabled = false
            binding.tvError.isVisible = false

            try {
                val src = toLocaleCode(sourceLangCode)
                val tgt = toLocaleCode(targetLangCode)
                val options = TranslateOptions().apply {
                    setStyle(TranslationStyle.FLUID)
                }

                val res = withContext(Dispatchers.IO) {
                    laraClient.translate(text, src, tgt, options)
                }

                binding.tvResult.text = res.translation.orEmpty()
                binding.cardResult.isVisible = true
            } catch (e: Exception) {
                showError(e.message ?: "Translation failed.")
            } finally {
                binding.progressBar.isVisible = false
                binding.btnTranslate.isEnabled = true
            }
        }
    }

    private fun startVoiceInput(lang: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, toLocaleCode(lang))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now…")
        }
        voiceLauncher.launch(intent)
    }

    private fun speakText(text: String, langCode: String) {
        val loc = languages.find { it.code == langCode }?.let {
            when (it.code) {
                "en" -> Locale.US
                "es" -> Locale("es")
                "fr" -> Locale.FRENCH
                "de" -> Locale.GERMAN
                "it" -> Locale.ITALIAN
                "pt" -> Locale("pt")
                "ja" -> Locale.JAPANESE
                "zh" -> Locale.SIMPLIFIED_CHINESE
                else -> Locale.getDefault()
            }
        } ?: Locale.getDefault()
        tts?.language = loc
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts")
    }

    private fun toLocaleCode(code: String): String =
        languages.firstOrNull { it.code == code }?.locale ?: "${code}-${code.uppercase()}"

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }
}
