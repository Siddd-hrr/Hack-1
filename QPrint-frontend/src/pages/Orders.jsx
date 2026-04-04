import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ClipboardList, Loader2 } from 'lucide-react';
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

const steps = ['PENDING', 'PROCESSING', 'READY', 'COMPLETED'];

export default function OrdersPage() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    let timer;

    const load = async (showSpinner = false) => {
      if (showSpinner) setLoading(true);
      try {
        const { data } = await api.get('/api/orders/active');
        setOrders(data?.data || []);
      } catch (err) {
        toast.error(getErrorMessage(err, 'Could not load orders'));
      } finally {
        setLoading(false);
      }
    };

    load(true);
    timer = setInterval(() => load(false), 10_000);

    return () => {
      if (timer) clearInterval(timer);
    };
  }, []);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <Spinner label="Loading orders" />
      </div>
    );
  }

  if (!orders.length) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background text-white px-4">
        <div className="glass-card max-w-md w-full p-8 text-center space-y-4 border border-white/10">
          <ClipboardList className="h-10 w-10 text-primary mx-auto" />
          <h1 className="text-2xl font-semibold">No orders yet</h1>
          <p className="text-gray-300 text-sm">Complete a checkout to see your orders here.</p>
          <div className="flex gap-3 justify-center">
            <Button variant="ghost" onClick={() => navigate('/upload')}>Upload & Print</Button>
            <Button variant="ghost" onClick={() => navigate('/cart')}>Go to Cart</Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background text-white flex flex-col">
      <Header />
      <div className="flex-1 px-4 py-6">
        <div className="max-w-6xl mx-auto space-y-6">
          <div className="flex items-center justify-between">
          <div>
            <p className="text-sm text-gray-400">Orders</p>
            <h1 className="text-2xl font-semibold">Your print orders</h1>
          </div>
        </div>

        <div className="grid gap-4">
          {orders.map((order, idx) => (
            <motion.div
              key={order.orderId}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: idx * 0.02 }}
              className="glass-card border border-white/10 p-4 flex flex-col gap-3"
            >
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-lg font-semibold">₹{Number(order.amount || 0).toFixed(2)}</p>
                  <p className="text-sm text-gray-400">{formatDate(order.createdAt)}</p>
                </div>
                <StatusBadge status={order.status} />
              </div>
              <div className="text-sm text-gray-300 space-y-1">
                <p>Checkout ID: {order.checkoutId}</p>
                <p>Payment ID: {order.paymentId || '—'}</p>
                <p>{order.items?.length || 0} item(s)</p>
              </div>

              <Progress status={order.status} />
              {order.status === 'READY' && order.otp && <OtpInline otp={order.otp} />}
              {order.status === 'READY' && !order.otp && <OtpGenerating />}
              <div className="flex justify-end">
                <Button size="sm" onClick={() => navigate(`/orders/${order.orderId}`)}>
                  View details
                </Button>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </div>
    </div>
  );
}

function StatusBadge({ status }) {
  const cls = statusTone[status] || statusTone.PENDING;
  return (
    <span className={`text-xs font-semibold rounded-full px-3 py-1 border ${cls}`}>
      {status}
    </span>
  );
}

function Progress({ status }) {
  const currentIdx = steps.indexOf(status);
  return (
    <div className="flex items-center gap-2 text-[11px] text-gray-300">
      {steps.map((step, idx) => {
        const active = currentIdx >= idx && currentIdx !== -1;
        return (
          <div key={step} className="flex items-center gap-1">
            <div
              className={`h-2.5 w-2.5 rounded-full border ${
                active ? 'bg-primary border-primary' : 'bg-transparent border-white/30'
              }`}
            />
            <span className={active ? 'text-white' : 'text-gray-500'}>{step}</span>
            {idx < steps.length - 1 && <span className="text-gray-600">→</span>}
          </div>
        );
      })}
    </div>
  );
}

function OtpInline({ otp }) {
  return (
    <div className="rounded-md border border-primary/40 bg-primary/10 px-3 py-2 text-sm text-white font-mono tracking-[0.4em]">
      {otp}
    </div>
  );
}

function OtpGenerating() {
  return (
    <div className="rounded-md border border-info/40 bg-info/10 px-3 py-2 text-sm text-gray-100 flex items-center gap-2">
      <Loader2 className="h-4 w-4 animate-spin text-info" />
      <span>Generating OTP…</span>
    </div>
  );
}

function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}
