import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import toast from 'react-hot-toast';
import {
  AlertCircle,
  CheckCircle2,
  FileText,
  Loader2,
  ShieldCheck,
  UploadCloud,
} from 'lucide-react';
import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist';
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

import Button from '../components/ui/Button';
import Header from '../components/Header';
import api from '../api/axios';
import { useCartStore } from '../store/cartStore';

GlobalWorkerOptions.workerSrc = pdfWorker;

const MAX_SIZE = 50 * 1024 * 1024;
const allowedMime = new Set([
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'image/png',
  'image/jpeg',
]);

const priceFromPrefs = (pageCount, prefs) => {
  const base = prefs.colorMode === 'COLOR' ? 8 : 1.5;
  const pages = prefs.sides === 'DOUBLE' ? Math.ceil(pageCount / 2) * prefs.copies : pageCount * prefs.copies;
  const multiplier = prefs.sides === 'DOUBLE' ? 0.9 : 1;
  const bindingCost = prefs.binding === 'SPIRAL' ? 15 : 0;
  return Number(((pages * base * multiplier) + bindingCost).toFixed(2));
};

const defaultPrefs = {
  copies: 1,
  colorMode: 'BW',
  sides: 'SINGLE',
  pageRange: 'ALL',
  paperSize: 'A4',
  binding: 'NONE',
  specialInstructions: '',
};

export default function UploadPage() {
  const [file, setFile] = useState(null);
  const [pageCount, setPageCount] = useState(1);
  const [prefs, setPrefs] = useState(defaultPrefs);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState(null);
  const inputRef = useRef(null);
  const navigate = useNavigate();
  const setItemCount = useCartStore((s) => s.setItemCount);

  const estimatedPrice = useMemo(() => priceFromPrefs(pageCount || 1, prefs), [pageCount, prefs]);

  useEffect(() => {
    setResult(null);
  }, [file]);

  const handleFileSelect = async (selected) => {
    if (!selected) return;
    if (selected.size > MAX_SIZE) {
      toast.error('File exceeds 50MB limit');
      return;
    }
    if (!allowedMime.has(selected.type)) {
      toast.error('Use PDF, DOCX, JPG, or PNG files');
      return;
    }
    setFile(selected);
    await computePages(selected);
  };

  const computePages = async (selected) => {
    try {
      if (selected.type === 'application/pdf') {
        const buffer = await selected.arrayBuffer();
        const pdf = await getDocument({ data: buffer }).promise;
        setPageCount(pdf.numPages || 1);
      } else {
        setPageCount(1);
      }
    } catch (err) {
      console.error(err);
      setPageCount(1);
      toast.error('Could not read file pages. Using 1 page as default.');
    }
  };

  const onSubmit = async () => {
    if (!file) {
      toast.error('Please select a file to upload.');
      return;
    }
    setUploading(true);
    try {
      const form = new FormData();
      form.append('file', file);
      form.append('preferences', JSON.stringify(prefs));

      const { data } = await api.post('/api/objects/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const uploaded = data?.data;
      if (uploaded?.objectId) {
        try {
          const addResponse = await api.post('/api/cart/add', { objectId: uploaded.objectId });
          const summary = addResponse?.data?.data;
          if (typeof summary?.totalItems === 'number') {
            setItemCount(summary.totalItems);
          }
          toast.success('Added to cart!');
        } catch (addErr) {
          toast.error(addErr?.response?.data?.message || 'Uploaded, but failed to add to cart');
        }
      }

      setResult(uploaded);
    } catch (err) {
      toast.error(err?.response?.data?.message || err.message || 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="min-h-screen bg-background text-white flex flex-col">
      <Header />
      <div className="flex-1 px-4 py-8">
        <div className="auth-bg" />
        <div className="max-w-6xl mx-auto grid gap-8 lg:grid-cols-[1.1fr_0.9fr] relative z-10">
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
          <div className="glass-card p-6 border border-white/10">
            <div className="flex items-center gap-3">
              <UploadCloud className="text-primary" />
              <div>
                <h1 className="text-2xl font-display font-semibold">Upload & Print</h1>
                <p className="text-gray-300 text-sm">Drag a file, set preferences, see price instantly.</p>
              </div>
            </div>
            <div className="mt-5 grid gap-3 sm:grid-cols-3 text-sm text-gray-300">
              <InfoPill icon={<ShieldCheck className="h-4 w-4" />} label="Secure upload" />
              <InfoPill icon={<FileText className="h-4 w-4" />} label="PDF, DOCX, JPG, PNG" />
              <InfoPill icon={<AlertCircle className="h-4 w-4" />} label="Max 50MB" />
            </div>
          </div>

          <div className="glass-card p-6 border border-white/10 space-y-6">
            <DropZone
              file={file}
              onSelect={() => inputRef.current?.click()}
              onDropFile={handleFileSelect}
            />
            <input
              ref={inputRef}
              type="file"
              accept=".pdf,.docx,.png,.jpg,.jpeg"
              className="hidden"
              onChange={(e) => handleFileSelect(e.target.files?.[0])}
            />

            {file && (
              <div className="grid gap-4 md:grid-cols-2">
                <PreferenceGroup label="Copies">
                  <input
                    type="number"
                    min={1}
                    max={100}
                    value={prefs.copies}
                    onChange={(e) => setPrefs((p) => ({ ...p, copies: Math.max(1, Math.min(100, Number(e.target.value) || 1)) }))}
                    className="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 focus:outline-none focus:border-primary/60 input-focus-ring"
                  />
                </PreferenceGroup>

                <PreferenceGroup label="Color Mode">
                  <Segmented
                    options={[
                      { value: 'BW', label: 'B&W (₹1.5/pg)' },
                      { value: 'COLOR', label: 'Color (₹8/pg)' },
                    ]}
                    value={prefs.colorMode}
                    onChange={(val) => setPrefs((p) => ({ ...p, colorMode: val }))}
                  />
                </PreferenceGroup>

                <PreferenceGroup label="Sides">
                  <Segmented
                    options={[
                      { value: 'SINGLE', label: 'Single Sided' },
                      { value: 'DOUBLE', label: 'Double (10% off)' },
                    ]}
                    value={prefs.sides}
                    onChange={(val) => setPrefs((p) => ({ ...p, sides: val }))}
                  />
                </PreferenceGroup>

                <PreferenceGroup label="Paper Size">
                  <Segmented
                    options={[
                      { value: 'A4', label: 'A4' },
                      { value: 'A3', label: 'A3' },
                      { value: 'LEGAL', label: 'Legal' },
                    ]}
                    value={prefs.paperSize}
                    onChange={(val) => setPrefs((p) => ({ ...p, paperSize: val }))}
                  />
                </PreferenceGroup>

                <PreferenceGroup label="Binding">
                  <Segmented
                    options={[
                      { value: 'NONE', label: 'None' },
                      { value: 'STAPLE', label: 'Staple (₹0)' },
                      { value: 'SPIRAL', label: 'Spiral (₹15)' },
                    ]}
                    value={prefs.binding}
                    onChange={(val) => setPrefs((p) => ({ ...p, binding: val }))}
                  />
                </PreferenceGroup>

                <PreferenceGroup label="Page Range">
                  <input
                    type="text"
                    value={prefs.pageRange}
                    onChange={(e) => setPrefs((p) => ({ ...p, pageRange: e.target.value }))}
                    className="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 focus:outline-none focus:border-primary/60 input-focus-ring"
                    placeholder="ALL or 1-5,8,10-12"
                  />
                  <p className="text-xs text-gray-400 mt-2">Leave as ALL to print everything.</p>
                </PreferenceGroup>

                <PreferenceGroup label="Special Instructions" full>
                  <textarea
                    rows={3}
                    value={prefs.specialInstructions}
                    onChange={(e) => setPrefs((p) => ({ ...p, specialInstructions: e.target.value }))}
                    className="w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 focus:outline-none focus:border-primary/60 input-focus-ring"
                    placeholder="Any binding notes or print preferences"
                  />
                </PreferenceGroup>
              </div>
            )}
          </div>
        </motion.div>

        <motion.div initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} className="space-y-4">
          <div className="glass-card p-6 border border-white/10 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-400">Estimated cost</p>
                <h2 className="text-3xl font-semibold">₹{estimatedPrice.toFixed(2)}</h2>
              </div>
              <div className="text-right">
                <p className="text-sm text-gray-400">Pages detected</p>
                <p className="text-xl font-semibold">{pageCount}</p>
              </div>
            </div>

            <div className="bg-white/5 border border-white/10 rounded-lg p-3 text-sm text-gray-200 space-y-1">
              <p>Copies: {prefs.copies}</p>
              <p>Mode: {prefs.colorMode === 'COLOR' ? 'Color' : 'B&W'}</p>
              <p>Sides: {prefs.sides === 'DOUBLE' ? 'Double (duplex)' : 'Single'}</p>
              <p>Binding: {prefs.binding}</p>
            </div>

            <Button onClick={onSubmit} disabled={!file} loading={uploading}>
              {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <UploadCloud className="h-4 w-4" />} 
              Upload & Add to Cart
            </Button>

            {result && (
              <div className="rounded-lg border border-success/30 bg-success/10 p-3 text-sm text-gray-100">
                <div className="flex items-center gap-2 text-success">
                  <CheckCircle2 className="h-4 w-4" /> Added to cart
                </div>
                <p className="mt-1">{result.originalFilename}</p>
                <p className="text-xs text-gray-200">Server price: ₹{result.calculatedPrice}</p>
                <div className="mt-2 flex gap-2">
                  <Button variant="ghost" onClick={() => navigate('/cart')}>Go to Cart</Button>
                  <Button variant="ghost" onClick={() => { setFile(null); setResult(null); setPrefs(defaultPrefs); }}>Upload another</Button>
                </div>
              </div>
            )}
          </div>

          <div className="glass-card p-5 border border-white/10 text-sm text-gray-300 space-y-2">
            <div className="flex items-center gap-2 text-primary">
              <FileText className="h-4 w-4" />
              <span>Tips</span>
            </div>
            <ul className="list-disc list-inside space-y-1">
              <li>Use PDFs for accurate page counts. DOCX/image files default to 1 page estimation here.</li>
              <li>Double-sided reduces cost by 10% and counts sheets instead of pages.</li>
              <li>Max file size 50MB per upload.</li>
            </ul>
          </div>
        </motion.div>
      </div>
    </div>
    </div>
  );
}

function DropZone({ file, onSelect, onDropFile }) {
  const handleDrop = (e) => {
    e.preventDefault();
    const dropped = e.dataTransfer.files?.[0];
    if (dropped) onDropFile(dropped);
  };

  return (
    <div
      onDrop={handleDrop}
      onDragOver={(e) => e.preventDefault()}
      className="border border-dashed border-white/20 rounded-xl p-6 bg-white/5 hover:border-primary/60 transition cursor-pointer"
      onClick={onSelect}
    >
      <div className="flex items-center gap-4">
        <div className="p-3 rounded-full bg-primary/10 text-primary">
          {file ? <FileText /> : <UploadCloud />}
        </div>
        <div className="flex-1">
          <p className="text-lg font-semibold">{file ? file.name : 'Drop your file here or click to browse'}</p>
          <p className="text-sm text-gray-400">PDF, DOCX, PNG, JPG • Max 50MB</p>
        </div>
      </div>
    </div>
  );
}

function PreferenceGroup({ label, children, full }) {
  return (
    <div className={full ? 'md:col-span-2' : ''}>
      <p className="text-sm text-gray-200 mb-2">{label}</p>
      {children}
    </div>
  );
}

function Segmented({ options, value, onChange }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
      {options.map((opt) => (
        <button
          key={opt.value}
          type="button"
          onClick={() => onChange(opt.value)}
          className={`rounded-lg border px-3 py-2 text-sm transition ${
            value === opt.value
              ? 'border-primary/70 bg-primary/15 text-white'
              : 'border-white/10 bg-white/5 text-gray-200 hover:border-primary/40'
          }`}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}

function InfoPill({ icon, label }) {
  return (
    <div className="flex items-center gap-2 rounded-full bg-white/5 px-3 py-2 border border-white/10">
      {icon}
      <span>{label}</span>
    </div>
  );
}
