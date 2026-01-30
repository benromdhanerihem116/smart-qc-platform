import React, { useState } from 'react';
import './App.css';

function App() {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [heatmap, setHeatmap] = useState(null);
  const [res, setRes] = useState(null);
  const [scanError, setScanError] = useState(null); // Pour gérer le rejet "Vigile"
  const [loading, setLoading] = useState(false);
  const [history, setHistory] = useState([]);

  const onUpload = (e) => {
    const f = e.target.files[0];
    if (f) {
      setFile(f);
      setPreview(URL.createObjectURL(f));
      setHeatmap(null);
      setRes(null);
      setScanError(null);
    }
  };

  const runScan = async () => {
    if(!file) return;
    setLoading(true);
    setHeatmap(null);
    setRes(null);
    setScanError(null);
    
    const fd = new FormData();
    fd.append("image", file);

    try {
      // Envoi de l'image au backend AI
      const r = await fetch("http://localhost:8080/upload", { method: "POST", body: fd });
      
      // 1. GESTION DES REJETS (Le Videur)
      if (!r.ok) {
         const errorData = await r.json();
         
         // Si c'est un rejet sémantique (Voiture, Fruit...)
         if(errorData.error === 'OBJECT_MISMATCH' || (errorData.message && errorData.message.includes("non industriel"))) {
             setScanError({
                 title: "HORS PÉRIMÈTRE",
                 message: errorData.message || "L'IA a détecté un objet non supporté.",
                 object: errorData.detected_object || "?"
             });
         } else {
             // Autre erreur technique
             setScanError({
                 title: "ERREUR TECHNIQUE",
                 message: "Impossible de traiter l'image."
             });
         }
         setLoading(false);
         return;
      }

      // 2. SUCCÈS (L'Expert a validé)
      const data = await r.json();
      
      setTimeout(() => {
        setRes(data);

        if(data.heatmap) {
            setHeatmap(`data:image/jpeg;base64,${data.heatmap}`);
        }

        setHistory(prev => [{
          id: Date.now(),
          type: data.is_defective ? "NOK" : "OK",
          score: data.prediction ? data.prediction.toFixed(1) : 0,
          time: new Date().toLocaleTimeString()
        }, ...prev].slice(0, 8));
        
        setLoading(false);
      }, 1000); 
      
    } catch (e) {
      console.error(e);
      setScanError({ title: "ERREUR RÉSEAU", message: "Vérifiez que Docker est lancé." });
      setLoading(false);
    }
  };

  const downloadReport = async () => {
    if(!res || !res.auditId) return;
    window.open(`http://localhost:8080/audit/${res.auditId}/pdf`, '_blank');
  };

  const successRate = history.length > 0 
    ? ((history.filter(h => h.type === "OK").length / history.length) * 100).toFixed(0) 
    : "--";

  return (
    <div className="dashboard">
      <aside className="sidebar">
        <div className="logo">
          SMART-QC <span style={{color:'white'}}>PLATFORM</span>
          <div style={{fontSize:'10px', color:'#888', letterSpacing:'1px', marginTop:'5px'}}>AUTOMATED QUALITY CONTROL</div>
        </div>
        
        <div style={{marginTop:'20px', marginBottom:'10px', fontSize:'12px', color:'var(--neon-blue)', fontWeight:'bold'}}>
          KPIs DE PRODUCTION
        </div>

        <div className="stat-card">
          <small style={{color:'#aaa', fontSize:'11px', letterSpacing:'1px'}}>UNITÉS AUDITÉES</small>
          <div className="stat-num">{history.length}</div>
        </div>

        <div className="stat-card">
          <small style={{color:'#aaa', fontSize:'11px', letterSpacing:'1px'}}>TAUX DE CONFORMITÉ</small>
          <div className="stat-num">{successRate}<span style={{fontSize:'14px'}}>%</span></div>
        </div>

        <h4 style={{marginTop:'40px', color:'var(--neon-blue)', borderBottom:'1px solid #333', paddingBottom:'5px', fontSize:'12px'}}>
          HISTORIQUE DES LOGS
        </h4>
        <div className="history-log">
          {history.length === 0 && <div style={{opacity:0.3, fontStyle:'italic', fontSize:'12px'}}>Flux de données en attente...</div>}
          {history.map(h => (
            <div key={h.id} className="log-item">
              <span style={{color:'#888', fontSize:'11px'}}>{h.time}</span>
              <span style={{color: h.type === 'OK' ? 'var(--neon-green)' : 'var(--neon-red)', fontWeight:'bold'}}>
                [{h.type}]
              </span>
              <span style={{fontSize:'11px'}}>Conf: {h.score}%</span>
            </div>
          ))}
        </div>
      </aside>

      <main className="main-view">
        <header className="header-bar">
          <div>
            <h1 style={{margin:0, fontSize:'22px', letterSpacing:'1px', textTransform:'uppercase'}}>
              CONTRÔLE NON DESTRUCTIF AUTOMATISÉ (CND)
            </h1>
            <div style={{fontSize:'12px', color:'#888', marginTop:'5px'}}>LIGNE D'INSPECTION #04 • FLUX EN DIRECT</div>
          </div>
          <div className="system-status">● SYSTÈME EN LIGNE (DOCKER)</div>
        </header>

        <div className="grid-container">
          
          <div className="scanner-frame">
            {loading && (
              <>
                <div className="laser-line"></div>
                <div className="laser-grid"></div>
                <div style={{position:'absolute', bottom:'20px', right:'20px', color:'var(--neon-blue)', background:'rgba(0,0,0,0.8)', padding:'5px 10px', border:'1px solid var(--neon-blue)', fontSize:'12px'}}>
                  ACQUISITION & ANALYSE NEURONALE...
                </div>
              </>
            )}

            {heatmap ? (
                <img src={heatmap} className="scanner-img" alt="XAI Analysis" style={{border: '2px solid var(--neon-blue)'}} />
            ) : preview ? (
              <img src={preview} className="scanner-img" alt="Scan Target" style={{opacity: loading ? 0.6 : 1, filter: loading ? 'grayscale(100%)' : 'none'}} />
            ) : (
              <div style={{display:'flex', flexDirection:'column', alignItems:'center', opacity:0.4}}>
                <div style={{fontSize:'40px', marginBottom:'10px'}}>📷</div>
                <div style={{letterSpacing:'2px'}}>EN ATTENTE DE PIÈCE</div>
              </div>
            )}
          </div>

          <div className="control-panel">
            
            <div className="panel-box">
              <h3 style={{marginTop:0, color:'var(--neon-blue)', fontSize:'16px', borderBottom:'1px solid rgba(0,210,255,0.1)', paddingBottom:'10px'}}>
                CONSOLE OPÉRATEUR
              </h3>
              
              <input type="file" id="file" onChange={onUpload} hidden />
              
              <div className="btn-group">
                <label htmlFor="file" className="btn" style={{textAlign:'center', display:'flex', alignItems:'center', justifyContent:'center'}}>
                  IMPORTER IMAGE
                </label>
                <button className="btn" onClick={() => {setFile(null); setPreview(null); setHeatmap(null); setRes(null); setScanError(null);}}>
                  R.A.Z
                </button>
              </div>

              <button className="btn btn-main" onClick={runScan} disabled={!file || loading}>
                {loading ? "TRAITEMENT EN COURS..." : "LANCER L'AUDIT IA"}
              </button>
            </div>

            {/* CAS 1 : ERREUR / REJET (Le Videur) */}
            {scanError && (
                <div className="alert-box" style={{borderLeft: '4px solid #ff9800', background: 'rgba(255, 152, 0, 0.1)'}}>
                    <div style={{fontSize:'11px', color:'#ff9800', marginBottom:'5px', fontWeight:'bold'}}>
                        ⚠️ {scanError.title}
                    </div>
                    <h2 style={{margin:'0 0 10px 0', fontSize:'18px', color:'white'}}>
                        IMAGE IGNORÉE
                    </h2>
                    <p style={{fontSize:'13px', color:'#ccc', lineHeight:'1.4'}}>
                        {scanError.message}
                    </p>
                    {scanError.object && (
                        <div style={{marginTop:'10px', fontSize:'12px', color:'#ff9800'}}>
                           Détecté : <strong>{scanError.object}</strong>
                        </div>
                    )}
                </div>
            )}

            {/* CAS 2 : SUCCÈS (L'Expert) */}
            {res && (
              <div className={`alert-box ${res.is_defective ? 'defect' : 'ok'}`}>
                <div style={{fontSize:'11px', opacity:0.7, marginBottom:'5px'}}>RÉSULTAT D'ANALYSE</div>
                <h2 style={{margin:'0 0 10px 0', fontSize:'22px', letterSpacing:'1px'}}>
                    {res.is_defective ? "DÉFAUT DÉTECTÉ" : "CONFORME"}
                </h2>
                
                {res.heatmap && (
                    <div style={{fontSize:'10px', color:'#aaa', marginBottom:'10px', fontStyle:'italic'}}>
                        * Zone d'intérêt identifiée par Grad-CAM
                    </div>
                )}

                {res.prediction && (
                  <div style={{marginTop:'20px'}}>
                    <div style={{display:'flex', justifyContent:'space-between', marginBottom:'5px', fontSize:'11px', textTransform:'uppercase'}}>
                      <span>Confiance IA</span>
                      <span>{res.prediction.toFixed(1)}%</span>
                    </div>
                    <div style={{background:'rgba(255,255,255,0.1)', height:'6px', borderRadius:'3px'}}>
                      <div style={{
                        width: `${res.prediction}%`, 
                        height:'100%', 
                        borderRadius:'3px',
                        background: res.is_defective ? 'var(--neon-red)' : 'var(--neon-green)',
                        boxShadow: `0 0 15px ${res.is_defective ? 'rgba(255, 42, 42, 0.5)' : 'rgba(0, 255, 136, 0.5)'}`
                      }}></div>
                    </div>
                  </div>
                )}

                <button className="btn" onClick={downloadReport} style={{marginTop:'20px', width:'100%', borderColor: 'var(--text-main)', color: 'var(--text-main)'}}>
                    ⬇ TÉLÉCHARGER CERTIFICAT PDF
                </button>
              </div>
            )}

          </div>
        </div>
      </main>
    </div>
  );
}

export default App;