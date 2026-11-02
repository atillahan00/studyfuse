package org.studyfuse.learning

import kotlinx.coroutines.*
import org.json.*
import org.studyfuse.core.*

/** Bounded contexts are a model constraint, not a limit on the number of source pages. */
object CoachBatches {
    fun plan(passages:List<EvidencePassage>,budget:Int=6000,limit:Int=4):List<List<EvidencePassage>> {
        require(budget>0 && limit>0)
        val batches=mutableListOf<List<EvidencePassage>>();var batch=mutableListOf<EvidencePassage>();var used=0
        for(p in passages) {
            require(p.text.length<=budget){"Kaynak parçası bağlama sığmıyor; kaynak okumasını yeniden oluşturun"}
            if(batch.isNotEmpty() && (batch.size==limit || used+p.text.length>budget)){batches+=batch;batch=mutableListOf();used=0}
            batch+=p;used+=p.text.length
        }
        if(batch.isNotEmpty())batches+=batch
        return batches
    }
}

class CoachRunner(private val tools:StudyTools,private val inference:ExamInference) {
    suspend fun generate(model:LocalModel,config:ExamConfig,task:CoachTask,query:String,attempt:String,passages:List<EvidencePassage>):JSONObject {
        var last:Exception?=null
        for(turn in 0..1) {
            currentCoroutineContext().ensureActive()
            val system=tools.system(config,task)+if(turn==0)"" else "\nREPAIR: the previous response had invalid structure/source references. Use only the provided E labels in evidence. Do not output quotes or invented labels. Return complete JSON and fewer cards if needed."
            // A native/runtime failure is not a citation error; do not blindly repeat inference.
            val raw=inference.generate(model,config.gpu,system,tools.prompt(query,attempt,passages),null)
            currentCoroutineContext().ensureActive()
            try {
                val result=tools.validate(raw,config.mode,passages,task)
                val invalidCards=result.getJSONArray("rejected").objects().any{it.optString("type")=="card"}
                if(turn==0 && task==CoachTask.CARDS && result.getJSONArray("cards").length()==0 && invalidCards)continue
                return result.put("repairAttempted",turn>0)
            }catch(e:CancellationException){throw e}catch(e:Exception){last=e}
        }
        throw IllegalArgumentException("Yanıtın kaynak bağlantıları veya biçimi düzeltilemedi; içerik doğrulanmış kartlara eklenmedi. Konu seçerek yeniden deneyebilirsiniz.",last)
    }

    /** Each completed group publishes cards immediately. Cancellation retains those checkpoints. */
    suspend fun cards(signature:String,model:LocalModel,config:ExamConfig,query:String,corpus:List<EvidencePassage>,topic:String="",progress:suspend(Int,Int,String)->Unit):JSONObject {
        require(corpus.isNotEmpty()){"Kart üretmek için önce kaynak okuması gerekli"}
        val batches=CoachBatches.plan(corpus)
        val report=JSONObject().put("signature",signature).put("query",query).put("topic",topic).put("totalBatches",batches.size).put("totalPassages",corpus.size)
            .put("processedBatches",0).put("reusedBatches",0).put("failedBatches",0).put("status","RUNNING").put("failures",JSONArray())
        val represented=linkedSetOf<String>();var done=0;var reused=0;var failures=0;var cards=0;var rejected=0
        fun checkpoint(){report.put("processedBatches",done).put("reusedBatches",reused).put("failedBatches",failures)
            .put("acceptedCards",cards).put("rejectedItems",rejected).put("passagesWithCards",represented.size)
            .put("passagesWithoutCards",JSONArray(corpus.filter{it.id !in represented}.map{JSONObject().put("id",it.id).put("source",it.source).put("page",it.page)}))
            atomicJson(tools.file("coach-run.json"),report)}
        checkpoint()
        try {
            for((index,passages) in batches.withIndex()) {
                currentCoroutineContext().ensureActive()
                val key=digest("coach-cards-v2|$signature|$query|"+passages.joinToString("|"){it.id+":"+digest(it.text)})
                val existing=tools.data("coach.json").optJSONArray("entries")?.objects()?.lastOrNull{it.optString("batchKey")==key && it.optString("signature")==signature}
                // Revalidate stored references against current source text. Never trust a corrupt checkpoint.
                val previous=existing?.getJSONObject("result")?.let{runCatching{tools.validate(it.toString(),config.mode,passages,CoachTask.CARDS).put("rejected",it.optJSONArray("rejected") ?: JSONArray())}.getOrNull()}
                val previousCards=previous?.getJSONArray("cards")?.objects().orEmpty()
                val pending=passages.filter{p->previousCards.none{it.getString("id")==p.id}}
                val cached=previous?.takeIf{previousCards.isNotEmpty() && pending.isEmpty()}
                progress(done,batches.size,"Kart grubu ${index+1}/${batches.size} · ${passages.first().source} · PDF ${passages.first().page}${if(cached!=null)" · önbellek" else ""}")
                try {
                    val response=if(cached!=null){reused++;cached}else {
                        val fresh=generate(model,config,CoachTask.CARDS,query,"",pending)
                        if(previousCards.isNotEmpty()) {
                            val rejectedFresh=fresh.getJSONArray("rejected")
                            val combined=JSONObject(fresh.toString()).put("cards",JSONArray(previousCards+fresh.getJSONArray("cards").objects()))
                                .put("citations",JSONArray())
                            tools.validate(combined.toString(),config.mode,passages,CoachTask.CARDS).put("rejected",rejectedFresh)
                        }else fresh
                    }
                    currentCoroutineContext().ensureActive()
                    tools.saveCoach(signature,CoachTask.CARDS,query,response,passages,key)
                    response.getJSONArray("cards").objects().forEach{represented+=it.getString("id");cards++}
                    rejected+=response.optJSONArray("rejected")?.length() ?: 0
                    if(response.getJSONArray("cards").length()==0 && (response.optJSONArray("rejected")?.length() ?: 0)>0){
                        failures++;report.getJSONArray("failures").put(JSONObject().put("batch",index+1).put("reason","Kaynak bağlantılı kart üretilemedi"))
                    }
                }catch(e:CancellationException){throw e}catch(e:Exception){failures++;report.getJSONArray("failures").put(JSONObject().put("batch",index+1).put("reason",e.message?.take(2000) ?: "Kart grubu işlenemedi"))}
                done++;checkpoint();progress(done,batches.size,"$cards kart hazır · $done/${batches.size} grup · $failures eksik grup")
            }
            report.put("status",if(failures>0 || represented.size<corpus.size)"PARTIAL" else "REVIEW");checkpoint();return report
        }catch(e:CancellationException){report.put("status","CANCELLED");checkpoint();throw e}
        catch(e:Exception){report.put("status","FAILED");checkpoint();throw e}
    }
}
