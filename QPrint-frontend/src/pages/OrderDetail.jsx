import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, Loader2, ShieldAlert } from 'lucide-react';
import toast from 'react-hot-toast';

import api from '../api/axios';
import Button from '../components/ui/Button';
import Spinner from '../components/ui/Spinner';
import Header from '../components/Header';
import { getErrorMessage } from '../utils/api';

const statusTone = {
  PENDING: 'bg-warning/10 border-warning/30 text-warning-foreground',
  PROCESSING: 'bg-primary/10 border-primary/40 text-primary',
  READY: 'bg-info/10 border-info/30 text-info-foreground',
  COMPLETED: 'bg-success/10 border-success/30 text-success',
  CANCELLED: 'bg-danger/10 border-danger/30 text-danger',
};

export default function OrderDetailPage() {
  const { orderId } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const isReady = order?.status === 'READY';
  const needsOtp = isReady && !order?.otp;

  const statusMeta = useMemo(() => {
    const st = order?.status || 'PENDING';
    return {
      label: st,
      tone: statusTone[st] || statusTone.PENDING,
    };
  }, [order]);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const { data } = await api.get(`/api/orders/${orderId}`);
        let ord = data?.data;

        if (ord?.status === 'READY' && !ord?.otp) {
          try {
            const active = await api.get(`/api/orders/active/${orderId}`);
            ord = active?.data?.data || ord;
          } catch (inner) {
            // ignore, will fall back to initial order data
          }
        }

        setOrder(ord);
      } catch (err) {
        const msg = getErrorMessage(err, 'Could not load order');
        setError(msg);
        toast.error(msg);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [orderId]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <Spinner label="Loading order" />
      </div>
    );
  }

  if (!order) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background text-white px-4">
        <div className="glass-card max-w-md w-full p-6 text-center border border-white/10 space-y-3">
          <ShieldAlert className="h-10 w-10 text-danger mx-auto" />
          <p className="font-semibold">Order not found</p>
          <Button variant="ghost" onClick={() => navigate('/orders')}>Back to orders</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background text-white flex flex-col">
      <Header />
      <div className="flex-1 px-4 py-6">
        <div className="max-w-6xl mx-auto space-y-6">
          <div className="flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="text-gray-400 hover:text-white">
            <ArrowLeft />
          </button>
          <div>
            <p className="text-sm text-gray-400">Order</p>
            <h1 className="text-2xl font-semibold">Order details</h1>
          </div>
        </div>

        {error && (
          <div className="glass-card border border-danger/40 bg-danger/10 p-3 text-sm text-gray-100 flex items-start gap-2">
            <ShieldAlert className="h-5 w-5 text-danger" />
            <p>{error}</p>
          </div>
        )}

        <div className="glass-card border border-white/10 p-5 space-y-4">
          <div className={`flex items-center justify-between rounded-lg border px-3 py-2 ${statusMeta.tone}`}>
            <span className="font-semibold text-sm">{statusMeta.label}</span>
            <span className="text-sm text-gray-200">{formatDate(order.createdAt)}</span>
          </div>

          {isReady && order.otp && <OtpDisplay otp={order.otp} />}
          {needsOtp && <GeneratingOtp />}

          <div className="grid gap-3 text-sm text-gray-200 sm:grid-cols-2">
            <Row label="Order ID" value={order.orderId} />
            <Row label="Checkout ID" value={order.checkoutId} />
            <Row label="Payment ID" value={order.paymentId || '—'} />
            <Row label="Amount" value={`₹${Number(order.amount || 0).toFixed(2)}`} />
            <Row label="Currency" value={order.currency || 'INR'} />
            {order.failureReason && <Row label="Failure reason" value={order.failureReason} />}
          </div>

          <div className="space-y-3">
            <p className="text-sm text-gray-300">Items</p>
            {order.items?.length ? (
              <div className="grid gap-3">
                {order.items.map((item, idx) => (
                  <motion.div
                    key={item.objectId || idx}
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: idx * 0.02 }}
                    className="rounded-lg border border-white/10 bg-white/5 p-3"
                  >
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="font-semibold">{item.filename}</p>
                        <p className="text-xs text-gray-400">Pages: {item.pageCount} · Copies: {item.copies}</p>
                      </div>
                      <span className="font-semibold">₹{Number(item.price || 0).toFixed(2)}</span>
                    </div>
                    <div className="text-[11px] text-gray-300 mt-1 space-x-2">
                      <span>{item.colorMode}</span>
                      <span>•</span>
                      <span>{item.sides}</span>
                      <span>•</span>
                      <span>{item.paperSize}</span>
                      <span>•</span>
                      <span>{item.binding}</span>
                      {item.pageRange && <span className="ml-2">Pages {item.pageRange}</span>}
                    </div>
                  </motion.div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-gray-300">No items found for this order.</p>
            )}
          </div>

          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => navigate('/orders')}>Back to orders</Button>
            <Button variant="ghost" onClick={() => navigate('/upload')}>Upload another</Button>
            <Button variant="ghost" onClick={() => navigate('/cart')}>Go to cart</Button>
          </div>
        </div>
      </div>
    </div>
    </div>
  );
}

function OtpDisplay({ otp }) {
  const digits = otp?.split('') || [];
  return (
    <div className="rounded-lg border border-primary/40 bg-primary/10 p-4">
      <p className="text-sm text-gray-200 mb-2">Show this OTP to collect your order:</p>
      <div className="flex gap-2 justify-start">
        {Array.from({ length: 6 }).map((_, idx) => (
          <div key={idx} className="h-12 w-10 rounded-lg border border-primary/50 bg-black/30 flex items-center justify-center text-2xl font-mono tracking-widest">
            {digits[idx] || '•'}
          </div>
        ))}
      </div>
    </div>
  );
}

function GeneratingOtp() {
  return (
    <div className="rounded-lg border border-info/30 bg-info/10 p-4 flex items-center gap-3 text-sm text-gray-100">
      <Loader2 className="h-5 w-5 animate-spin text-info" />
      <div>
        <p className="font-semibold">Generating OTP</p>
        <p className="text-xs text-gray-200">Please stay on this page; it will appear shortly.</p>
      </div>
    </div>
  );
}

function Row({ label, value }) {
  return (
    <div className="flex items-center justify-between rounded-md border border-white/5 bg-white/5 px-3 py-2">
      <span className="text-gray-300 text-sm">{label}</span>
      <span className="text-sm font-semibold break-all">{value}</span>
    </div>
  );
}

function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}
