import * as Haptics from 'expo-haptics';
import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text as RNText,
  TextInput,
  View,
  type TextInputProps,
  type TextProps as RNTextProps,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Line, Path } from 'react-native-svg';

import { radius, space, type, useColors } from '@/theme';

function tap() {
  Haptics.selectionAsync().catch(() => undefined);
}

// ---------------------------------------------------------------------------
// Text

type Variant = keyof typeof type;
type Tone = 'default' | 'muted' | 'faint' | 'inverse';

interface TextProps extends RNTextProps {
  variant?: Variant;
  tone?: Tone;
}

export function Text({ variant = 'body', tone = 'default', style, ...rest }: TextProps) {
  const c = useColors();
  const color = {
    default: c.foreground,
    muted: c.muted,
    faint: c.faint,
    inverse: c.inverseForeground,
  }[tone];
  return <RNText {...rest} style={[type[variant], { color }, style]} />;
}

// ---------------------------------------------------------------------------
// Screen

interface ScreenProps {
  children: ReactNode;
  /** Pinned to the bottom, above the keyboard (e.g. the primary button). */
  footer?: ReactNode;
  /** Shown above the content (e.g. back button, progress). */
  header?: ReactNode;
  scroll?: boolean;
}

export function Screen({ children, footer, header, scroll = false }: ScreenProps) {
  const c = useColors();
  const insets = useSafeAreaInsets();
  const Body = scroll ? ScrollView : View;
  return (
    <KeyboardAvoidingView
      style={[styles.flex, { backgroundColor: c.background }]}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={[styles.flex, styles.screen, { paddingTop: insets.top + space.m }]}>
        {header}
        <Body
          style={styles.flex}
          contentContainerStyle={scroll ? styles.scrollContent : undefined}
          keyboardShouldPersistTaps="handled"
        >
          {children}
        </Body>
        {footer ? (
          <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, space.m) + space.s }]}>
            {footer}
          </View>
        ) : null}
      </View>
    </KeyboardAvoidingView>
  );
}

// ---------------------------------------------------------------------------
// Buttons

interface ButtonProps {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
}

/** The one primary action on a screen: inverted, full width, pill. */
export function PrimaryButton({ label, onPress, disabled, loading }: ButtonProps) {
  const c = useColors();
  const inactive = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: inactive }}
      disabled={inactive}
      onPress={() => {
        tap();
        onPress();
      }}
      style={({ pressed }) => [
        styles.primary,
        { backgroundColor: c.inverseBackground, opacity: disabled ? 0.25 : pressed ? 0.8 : 1 },
      ]}
    >
      {loading ? (
        <ActivityIndicator color={c.inverseForeground} />
      ) : (
        <Text variant="label" tone="inverse">
          {label}
        </Text>
      )}
    </Pressable>
  );
}

export function TextButton({
  label,
  onPress,
  tone = 'muted',
  align = 'center',
}: ButtonProps & { tone?: Tone; align?: 'center' | 'flex-start' }) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      hitSlop={12}
      style={({ pressed }) => [styles.textButton, { alignSelf: align, opacity: pressed ? 0.6 : 1 }]}
    >
      <Text variant="label" tone={tone}>
        {label}
      </Text>
    </Pressable>
  );
}

export function IconButton({
  icon,
  onPress,
  label,
}: {
  icon: IconName;
  onPress: () => void;
  label: string;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      hitSlop={12}
      style={({ pressed }) => [styles.iconButton, { opacity: pressed ? 0.6 : 1 }]}
    >
      <Icon name={icon} />
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// Inputs

interface BigInputProps extends TextInputProps {
  suffix?: string;
}

/** Borderless, large input: the text itself is the UI. */
export function BigInput({ suffix, style, ...rest }: BigInputProps) {
  const c = useColors();
  const input = (
    <TextInput
      placeholderTextColor={c.faint}
      selectionColor={c.foreground}
      cursorColor={c.foreground}
      {...rest}
      style={[type.title, styles.bigInput, suffix ? StyleSheet.absoluteFill : null, { color: c.foreground }, style]}
    />
  );
  if (!suffix) return input;
  // With a suffix the input sizes to its content, so the unit sits right next to the number:
  // an invisible copy of the text defines the width, the input is laid over it.
  return (
    <View style={styles.bigInputRow}>
      <View>
        <Text variant="title" style={styles.sizer} accessibilityElementsHidden importantForAccessibility="no">
          {rest.value || rest.placeholder || ' '}
        </Text>
        {input}
      </View>
      <Text variant="title" tone="muted" style={styles.suffix}>
        {suffix}
      </Text>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Segmented control

interface SegmentedProps<T extends string> {
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
}

export function Segmented<T extends string>({ value, options, onChange }: SegmentedProps<T>) {
  const c = useColors();
  return (
    <View style={[styles.segmented, { borderColor: c.hairline }]}>
      {options.map((option) => {
        const active = option.value === value;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            onPress={() => {
              if (!active) tap();
              onChange(option.value);
            }}
            style={[styles.segment, active && { backgroundColor: c.inverseBackground }]}
          >
            <Text variant="caption" tone={active ? 'inverse' : 'muted'}>
              {option.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Lists

export function SectionTitle({ children }: { children: string }) {
  return (
    <Text variant="caption" tone="muted" style={styles.sectionTitle}>
      {children.toUpperCase()}
    </Text>
  );
}

interface RowProps {
  label: string;
  value?: ReactNode;
  onPress?: () => void;
  tone?: Tone;
}

/** A list row separated by a hairline. No cards, no shadows. */
export function Row({ label, value, onPress, tone = 'default' }: RowProps) {
  const c = useColors();
  const content = (
    <View style={[styles.row, { borderBottomColor: c.hairline }]}>
      <Text variant="body" tone={tone}>
        {label}
      </Text>
      <View style={styles.rowValue}>
        {typeof value === 'string' ? (
          <Text variant="body" tone="muted">
            {value}
          </Text>
        ) : (
          value
        )}
      </View>
    </View>
  );
  if (!onPress) return content;
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => ({ opacity: pressed ? 0.6 : 1 })}>
      {content}
    </Pressable>
  );
}

export function RowInput(props: TextInputProps) {
  const c = useColors();
  return (
    <TextInput
      placeholderTextColor={c.faint}
      selectionColor={c.foreground}
      {...props}
      style={[type.body, styles.rowInput, { color: c.muted }, props.style]}
    />
  );
}

// ---------------------------------------------------------------------------
// Progress

export function Progress({ step, total }: { step: number; total: number }) {
  const c = useColors();
  return (
    <View style={styles.progress} accessibilityRole="progressbar" accessibilityValue={{ min: 1, max: total, now: step }}>
      {Array.from({ length: total }, (_, i) => (
        <View
          key={i}
          style={[styles.progressSegment, { backgroundColor: i < step ? c.foreground : c.hairline }]}
        />
      ))}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Icons: thin line icons only

export type IconName = 'back' | 'settings' | 'check';

export function Icon({ name, size = 24, color }: { name: IconName; size?: number; color?: string }) {
  const c = useColors();
  const stroke = color ?? c.foreground;
  const common = { stroke, strokeWidth: 1.5, strokeLinecap: 'round' as const, fill: 'none' };
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {name === 'back' && <Path d="M15 5l-7 7 7 7" {...common} strokeLinejoin="round" />}
      {name === 'check' && <Path d="M5 12.5l4.5 4.5L19 7.5" {...common} strokeLinejoin="round" />}
      {name === 'settings' && (
        <>
          <Line x1={4} y1={8} x2={20} y2={8} {...common} />
          <Line x1={4} y1={16} x2={20} y2={16} {...common} />
          <Circle cx={9} cy={8} r={2.25} {...common} fill={c.background} />
          <Circle cx={15} cy={16} r={2.25} {...common} fill={c.background} />
        </>
      )}
    </Svg>
  );
}

// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  flex: { flex: 1 },
  screen: { paddingHorizontal: space.l },
  scrollContent: { paddingBottom: space.xxl },
  footer: { paddingTop: space.m, gap: space.m },
  primary: {
    height: 56,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  textButton: { paddingVertical: space.xs },
  iconButton: { width: 40, height: 40, alignItems: 'center', justifyContent: 'center', marginLeft: -8 },
  bigInputRow: { flexDirection: 'row', alignItems: 'baseline' },
  bigInput: { padding: 0, outlineStyle: 'none' } as object,
  sizer: { opacity: 0, paddingRight: 4, minWidth: 24 },
  suffix: { marginLeft: space.s },
  segmented: {
    flexDirection: 'row',
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: radius.pill,
    padding: 2,
    alignSelf: 'flex-start',
  },
  segment: {
    paddingHorizontal: space.m,
    paddingVertical: 6,
    borderRadius: radius.pill,
  },
  sectionTitle: { marginTop: space.xl, marginBottom: space.s, letterSpacing: 0.6 },
  row: {
    minHeight: 56,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: StyleSheet.hairlineWidth,
    gap: space.m,
  },
  rowValue: { flexShrink: 1, alignItems: 'flex-end' },
  rowInput: { textAlign: 'right', minWidth: 120, padding: 0, outlineStyle: 'none' } as object,
  progress: { flexDirection: 'row', gap: space.xs, marginBottom: space.xl },
  progressSegment: { flex: 1, height: 2, borderRadius: 1 },
});
