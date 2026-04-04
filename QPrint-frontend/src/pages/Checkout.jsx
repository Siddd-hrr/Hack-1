import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { AlertTriangle, ArrowLeft, CreditCard, Loader2, ShieldCheck } from 'lucide-react';
import toast from 'react-hot-toast';

import api from '../api/axios';
import Button from '../components/ui/Button';
import Spinner from '../components/ui/Spinner';
import Header from '../components/Header';
import { useAuthStore } from '../store/authStore';
import { useCartStore } from '../store/cartStore';
import { getErrorMessage, loadScript } from '../utils/api';

const RAZORPAY_SRC = 'https://checkout.razorpay.com/v1/checkout.js';

export default function CheckoutPage() {
  const [loading, setLoading] = useState(true);
  const [checkout, setCheckout] = useState(null);
  const [razorpayKey, setRazorpayKey] = useState('');
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const setCount = useCartStore((s) => s.setItemCount);

  const totals = useMemo(() => {
    const total = checkout?.amount ? Number(checkout.amount) : 0;
    return {
      subtotal: total,
      total,
    };
  }, [checkout]);

  useEffect(() => {
    const fetchCheckout = async () => {
      setLoading(true);
      try {
        const { data } = await api.post('/api/checkout/initiate');
        const payload = data?.data;
        setCheckout(payload?.checkout);
        setRazorpayKey(payload?.razorpayKeyId || '');
        setCount(payload?.checkout?.items?.length || 0);
      } catch (err) {
        setError(getErrorMessage(err, 'Failed to start checkout'));
      } finally {
        setLoading(false);
      }
    };

    fetchCheckout();
  }, [setCount]);

  const startPayment = async () => {
    if (!checkout) return;
    setPaying(true);
    try {
      const ok = await loadScript(RAZORPAY_SRC);
      if (!ok) {
        throw new Error('Payment SDK failed to load. Check your network and try again.');
      }
      if (!razorpayKey) {
        throw new Error('Razorpay key is not configured.');
      }

      const options = {
        key: razorpayKey,
        amount: Math.round(Number(checkout.amount) * 100),
        currency: checkout.currency || 'INR',
        name: 'QPrint',
        description: 'Print order payment',
        order_id: checkout.razorpayOrderId,
        notes: { checkoutId: checkout.checkoutId },
        prefill: {
          name: user?.fullName || user?.name || '',
          email: user?.email || '',
          contact: user?.phone || '',
        },
        theme: { color: '#7c3aed' },
        handler: (response) => {
          toast.success('Payment initiated');
          navigate(`/checkout/success?checkoutId=${checkout.checkoutId}&paymentId=${response?.razorpay_payment_id || ''}`);
        },
        modal: {
          ondismiss: () => setPaying(false),
        },
      };

      const rzp = new window.Razorpay(options);
      rzp.on('payment.failed', (resp) => {
        toast.error(resp?.error?.description || 'Payment failed');
        setPaying(false);
      });
      rzp.open();
    } catch (err) {
      toast.error(err?.message || 'Could not start payment');
      setPaying(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <Spinner label="Preparing checkout" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background text-white px-4">
        <div className="glass-card max-w-md w-full p-6 border border-danger/30 bg-danger/10 space-y-4 text-center">
          <AlertTriangle className="h-10 w-10 text-danger mx-auto" />
          <h1 className="text-xl font-semibold">Checkout unavailable</h1>
          <p className="text-gray-200 text-sm">{error}</p>
          <div className="flex gap-2 justify-center">
            <Button variant="ghost" onClick={() => navigate('/cart')}>Back to Cart</Button>
            <Button variant="ghost" onClick={() => navigate('/upload')}>Upload more</Button>
          </div>
        </div>
      </div>
    );
  }

  if (!checkout) {
    return null;
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
            <p className="text-sm text-gray-400">Checkout</p>
            <h1 className="text-2xl font-semibold">Complete your payment</h1>
          </div>
        </div>

        <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
          <div className="space-y-4">
            {checkout.items?.map((item, idx) => (
              <motion.div
                key={item.objectId}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: idx * 0.03 }}
                className="glass-card border border-white/10 p-4 flex flex-col gap-3"
              >
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-lg font-semibold">{item.filename}</p>
                    <p className="text-sm text-gray-400">Pages: {item.pageCount} · Copies: {item.copies}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-semibold">₹{Number(item.price || 0).toFixed(2)}</p>
                    <p className="text-xs text-gray-400">{item.colorMode} · {item.sides} · {item.paperSize}</p>
                  </div>
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs text-gray-300">
                  <Info tag="Binding" value={item.binding} />
                  {item.pageRange && <Info tag="Pages" value={item.pageRange} />}
                  <Info tag="Order ID" value={checkout.razorpayOrderId} />
                </div>
              </motion.div>
            ))}

            {!checkout.items?.length && (
              <div className="glass-card border border-white/10 p-4 text-sm text-gray-300">
                No items found for this checkout. Please return to your cart.
              </div>
            )}
          </div>

          <div className="glass-card border border-white/10 p-5 space-y-4 h-fit">
            <h2 className="text-xl font-semibold">Payment Summary</h2>
            <div className="space-y-2 text-sm text-gray-200">
              <Row label="Subtotal" value={`₹${totals.subtotal.toFixed(2)}`} />
              <Row label="Convenience fee" value="₹0.00" />
            </div>
            <div className="border-t border-white/10 pt-3 flex items-center justify-between">
              <span className="text-gray-300">Total</span>
              <span className="text-2xl font-semibold">₹{totals.total.toFixed(2)}</span>
            </div>
            <Button onClick={startPayment} disabled={paying}>
              {paying ? <Loader2 className="h-4 w-4 animate-spin" /> : <CreditCard className="h-4 w-4" />}
              {paying ? 'Opening Razorpay...' : 'Pay with Razorpay'}
            </Button>
            <div className="bg-white/5 border border-white/10 rounded-lg p-3 text-xs text-gray-300 flex items-start gap-2">
              <ShieldCheck className="h-4 w-4 text-primary" />
              <p>We will confirm payment after Razorpay completes processing. Do not close the window until you see a status update.</p>
            </div>
          </div>
        </div>
      </div>
    </div>
    </div>
  );
}

function Row({ label, value }) {
  return (
    <div className="flex items-center justify-between text-sm">
      <span className="text-gray-300">{label}</span>
      <span className="font-semibold">{value}</span>
    </div>
  );
}

function Info({ tag, value }) {
  return (
    <div className="rounded-md bg-white/5 border border-white/10 px-3 py-2">
      <p className="text-[11px] uppercase tracking-wide text-gray-400">{tag}</p>
      <p className="text-sm text-white break-all">{value}</p>
    </div>
  );
}
