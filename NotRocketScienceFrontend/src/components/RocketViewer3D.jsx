import { useState, useEffect, useRef } from "react";
import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls";

/**
 * Renders an interactive Three.js 3D viewer for a given .glb model.
 * Supports orbit controls (drag to rotate, scroll to zoom).
 * Cleans up the WebGL context and event listeners on unmount.
 *
 * @param {string} modelUrl - Path to the .glb file (e.g. "/models/falcon9.glb")
 */
function RocketViewer3D({ modelUrl }) {
  const mountRef  = useRef(null);
  const sceneRef  = useRef({});
  const [loading3d, setLoading3d] = useState(true);
  const [error3d, setError3d]     = useState(false);

  useEffect(() => {
    if (!modelUrl || !mountRef.current) return;
    let animId;
    let renderer;

    const init = async () => {
      try {
        const container = mountRef.current;
        const W = container.offsetWidth  || 800;
        const H = container.offsetHeight || 460;

        // Scene
        const scene = new THREE.Scene();
        scene.background = new THREE.Color("#0d1117");

        const grid = new THREE.GridHelper(20, 30, 0x003344, 0x001122);
        scene.add(grid);

        // Camera
        const camera = new THREE.PerspectiveCamera(50, W / H, 0.001, 5000);
        camera.position.set(0, 2, 10);

        // Renderer
        renderer = new THREE.WebGLRenderer({ antialias: true });
        renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
        renderer.setSize(W, H);
        container.appendChild(renderer.domElement);

        // Lighting — three-point setup for clean model presentation
        scene.add(new THREE.AmbientLight(0xffffff, 2.0));
        const sun  = new THREE.DirectionalLight(0xffffff, 2.0);
        sun.position.set(10, 20, 15);
        scene.add(sun);
        const fill = new THREE.DirectionalLight(0xffffff, 1.0);
        fill.position.set(-10, 5, -10);
        scene.add(fill);
        const under = new THREE.DirectionalLight(0xffffff, 0.5);
        under.position.set(0, -10, 5);
        scene.add(under);

        // Controls
        const controls = new OrbitControls(camera, renderer.domElement);
        controls.enableDamping    = true;
        controls.dampingFactor    = 0.07;
        controls.enablePan        = true;
        controls.autoRotate       = true;
        controls.autoRotateSpeed  = 0.5;
        sceneRef.current = { camera, renderer, controls };

        // Responsive resize handler
        const onResize = () => {
          const w = container.clientWidth;
          const h = container.clientHeight || 460;
          if (!w || !h) return;
          camera.aspect = w / h;
          camera.updateProjectionMatrix();
          renderer.setSize(w, h);
        };
        onResize();
        window.addEventListener("resize", onResize);

        // Load model and fit camera to bounding box
        const loader = new GLTFLoader();
        loader.load(modelUrl, (gltf) => {
          const model = gltf.scene;
          scene.add(model);

          const box    = new THREE.Box3().setFromObject(model);
          const centre = box.getCenter(new THREE.Vector3());
          const size   = box.getSize(new THREE.Vector3());
          const maxDim = Math.max(size.x, size.y, size.z);

          model.position.sub(centre);

          const fovRad  = (camera.fov * Math.PI) / 180;
          const fitDist = (maxDim / (2 * Math.tan(fovRad / 2))) * 2.0;
          camera.position.set(fitDist * 0.3, fitDist * 0.15, fitDist);
          camera.near = fitDist / 200;
          camera.far  = fitDist * 20;
          camera.updateProjectionMatrix();

          controls.target.set(0, 0, 0);
          controls.minDistance = fitDist * 0.15;
          controls.maxDistance = fitDist * 8;
          controls.update();

          grid.position.y = -(size.y / 2);
          onResize();
          setLoading3d(false);
        }, undefined, (err) => {
          console.error("GLB load error:", err);
          setError3d(true);
          setLoading3d(false);
        });

        // Render loop
        const animate = () => {
          animId = requestAnimationFrame(animate);
          controls.update();
          renderer.render(scene, camera);
        };
        animate();
        sceneRef.current.cleanup = () => window.removeEventListener("resize", onResize);

      } catch (e) {
        console.error("3D init error:", e);
        setError3d(true);
        setLoading3d(false);
      }
    };

    // Wait for the container to have a non-zero width before initialising
    let ro;
    const container = mountRef.current;
    ro = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.contentRect.width > 0) {
          ro.disconnect();
          init(container);
          break;
        }
      }
    });
    ro.observe(container);

    return () => {
      ro?.disconnect();
      cancelAnimationFrame(animId);
      sceneRef.current?.controls?.dispose();
      sceneRef.current?.cleanup?.();
      if (renderer) {
        renderer.dispose();
        if (renderer.domElement?.parentNode === mountRef.current) {
          mountRef.current.removeChild(renderer.domElement);
        }
      }
    };
  }, [modelUrl]);

  if (error3d) {
    return (
      <div style={{
        width: "100%", height: "100%", display: "flex",
        alignItems: "center", justifyContent: "center",
        color: "#ff6666", fontFamily: "Orbitron,monospace", fontSize: 11,
      }}>
        MODEL LOAD FAILED
      </div>
    );
  }

  return (
    <div style={{ position: "relative", width: "100%", height: "100%" }}>
      <div ref={mountRef} style={{ width: "100%", height: "100%" }} />

      {loading3d && (
        <div style={{
          position: "absolute", inset: 0, display: "flex", flexDirection: "column",
          alignItems: "center", justifyContent: "center", background: "#0d1117", gap: 14,
        }}>
          <div style={{
            width: 36, height: 36, border: "2px solid rgba(0,229,255,.15)",
            borderTopColor: "#00e5ff", borderRadius: "50%",
            animation: "spin .7s linear infinite",
          }} />
          <span style={{ color: "#00e5ff", fontFamily: "Orbitron,monospace", fontSize: 9, letterSpacing: "0.15em" }}>
            LOADING 3D MODEL
          </span>
        </div>
      )}

      {!loading3d && (
        <div style={{
          position: "absolute", bottom: 14, left: "50%", transform: "translateX(-50%)",
          background: "rgba(0,0,0,0.6)", borderRadius: 6, padding: "4px 18px",
          color: "rgba(0,229,255,0.6)", fontFamily: "Orbitron,monospace", fontSize: 8,
          letterSpacing: "0.12em", whiteSpace: "nowrap", pointerEvents: "none",
          border: "1px solid rgba(0,229,255,0.15)",
        }}>
          DRAG · ROTATE · SCROLL TO ZOOM
        </div>
      )}
    </div>
  );
}

export default RocketViewer3D;
